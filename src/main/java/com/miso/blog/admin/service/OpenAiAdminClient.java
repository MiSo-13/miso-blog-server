package com.miso.blog.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.admin.dto.OpenAiCostChartResponse;
import com.miso.blog.admin.dto.OpenAiUsageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAiAdminClient {
    public static final String KEY_TYPE_ADMIN = "ADMIN";
    public static final String KEY_TYPE_PROJECT = "PROJECT";
    public static final String KEY_TYPE_UNKNOWN = "UNKNOWN";

    private static final String COSTS_ENDPOINT = "https://api.openai.com/v1/organization/costs";
    private static final String COMPLETIONS_USAGE_ENDPOINT = "https://api.openai.com/v1/organization/usage/completions";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${blog.ai.admin-key:}")
    private String adminKey;

    @Value("${blog.ai.api-key:}")
    private String apiKey;

    public OpenAiCostSummary fetchCurrentMonthCostSummary() {
        String unavailableReason = getOrganizationApiUnavailableReason();
        if (unavailableReason != null) {
            return OpenAiCostSummary.unavailable(unavailableReason);
        }

        try {
            LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
            LocalDate monthStartUtc = todayUtc.withDayOfMonth(1);

            OpenAiCostChartResponse chart = fetchCostChart(monthStartUtc, todayUtc, List.of());
            BigDecimal todayCost = BigDecimal.ZERO;
            for (OpenAiCostChartResponse.Bucket bucket : chart.buckets()) {
                LocalDate bucketDate = bucket.startAt().toLocalDate();
                if (todayUtc.equals(bucketDate)) {
                    todayCost = todayCost.add(bucket.costUsd());
                }
            }

            return OpenAiCostSummary.available(todayCost, chart.totalCostUsd());
        } catch (OpenAiAdminClientException exception) {
            return OpenAiCostSummary.unavailable(exception.getMessage());
        }
    }

    public OpenAiCostChartResponse fetchCostChart(LocalDate startDate, LocalDate endDate, List<String> groupBy) {
        validateOrganizationApiKey();

        LocalDate normalizedStartDate = startDate == null ? LocalDate.now(ZoneOffset.UTC).minusDays(6) : startDate;
        LocalDate normalizedEndDate = endDate == null ? LocalDate.now(ZoneOffset.UTC) : endDate;
        validateDateRange(normalizedStartDate, normalizedEndDate);

        long startTime = normalizedStartDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long endTime = normalizedEndDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        int limit = (int) Math.min(normalizedEndDate.toEpochDay() - normalizedStartDate.toEpochDay() + 1, 180);

        JsonNode root = fetchJson(buildCostsUrl(startTime, endTime, groupBy, limit));
        List<OpenAiCostChartResponse.Bucket> buckets = new ArrayList<>();
        BigDecimal totalCostUsd = BigDecimal.ZERO;

        for (JsonNode bucketNode : root.path("data")) {
            List<OpenAiCostChartResponse.Result> results = new ArrayList<>();
            BigDecimal bucketTotal = BigDecimal.ZERO;

            for (JsonNode resultNode : bucketNode.path("results")) {
                BigDecimal amount = resultNode.path("amount").path("value").decimalValue();
                bucketTotal = bucketTotal.add(amount);
                results.add(new OpenAiCostChartResponse.Result(
                        textOrNull(resultNode, "project_id"),
                        textOrNull(resultNode, "line_item"),
                        amount,
                        resultNode.path("amount").path("currency").asText("usd")
                ));
            }

            totalCostUsd = totalCostUsd.add(bucketTotal);
            buckets.add(new OpenAiCostChartResponse.Bucket(
                    toUtcDateTime(bucketNode.path("start_time").asLong()),
                    toUtcDateTime(bucketNode.path("end_time").asLong()),
                    bucketTotal,
                    results
            ));
        }

        return new OpenAiCostChartResponse(
                normalizedStartDate.atStartOfDay(),
                normalizedEndDate.plusDays(1).atStartOfDay(),
                "1d",
                totalCostUsd,
                buckets
        );
    }

    public OpenAiUsageResponse fetchCompletionUsage(LocalDate startDate, LocalDate endDate, String bucketWidth, List<String> groupBy) {
        validateOrganizationApiKey();

        LocalDate normalizedStartDate = startDate == null ? LocalDate.now(ZoneOffset.UTC).minusDays(6) : startDate;
        LocalDate normalizedEndDate = endDate == null ? LocalDate.now(ZoneOffset.UTC) : endDate;
        validateDateRange(normalizedStartDate, normalizedEndDate);

        String normalizedBucketWidth = normalizeBucketWidth(bucketWidth);
        List<String> normalizedGroupBy = groupBy == null || groupBy.isEmpty() ? List.of("model") : groupBy;

        long startTime = normalizedStartDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long endTime = normalizedEndDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        int limit = determineUsageLimit(normalizedStartDate, normalizedEndDate, normalizedBucketWidth);

        JsonNode root = fetchJson(buildUsageUrl(startTime, endTime, normalizedBucketWidth, normalizedGroupBy, limit));
        List<OpenAiUsageResponse.Bucket> buckets = new ArrayList<>();
        long totalInputTokens = 0;
        long totalCachedInputTokens = 0;
        long totalOutputTokens = 0;
        long totalRequests = 0;

        for (JsonNode bucketNode : root.path("data")) {
            List<OpenAiUsageResponse.Result> results = new ArrayList<>();
            long bucketInputTokens = 0;
            long bucketCachedInputTokens = 0;
            long bucketOutputTokens = 0;
            long bucketRequests = 0;

            for (JsonNode resultNode : bucketNode.path("results")) {
                long inputTokens = resultNode.path("input_tokens").asLong(0);
                long cachedInputTokens = resultNode.path("input_cached_tokens").asLong(0);
                long outputTokens = resultNode.path("output_tokens").asLong(0);
                long requests = resultNode.path("num_model_requests").asLong(0);

                bucketInputTokens += inputTokens;
                bucketCachedInputTokens += cachedInputTokens;
                bucketOutputTokens += outputTokens;
                bucketRequests += requests;

                results.add(new OpenAiUsageResponse.Result(
                        textOrNull(resultNode, "model"),
                        textOrNull(resultNode, "api_key_id"),
                        textOrNull(resultNode, "project_id"),
                        textOrNull(resultNode, "user_id"),
                        resultNode.hasNonNull("batch") ? resultNode.path("batch").asBoolean() : null,
                        textOrNull(resultNode, "service_tier"),
                        inputTokens,
                        cachedInputTokens,
                        outputTokens,
                        requests
                ));
            }

            totalInputTokens += bucketInputTokens;
            totalCachedInputTokens += bucketCachedInputTokens;
            totalOutputTokens += bucketOutputTokens;
            totalRequests += bucketRequests;

            buckets.add(new OpenAiUsageResponse.Bucket(
                    toUtcDateTime(bucketNode.path("start_time").asLong()),
                    toUtcDateTime(bucketNode.path("end_time").asLong()),
                    bucketInputTokens,
                    bucketCachedInputTokens,
                    bucketOutputTokens,
                    bucketRequests,
                    results
            ));
        }

        return new OpenAiUsageResponse(
                normalizedStartDate.atStartOfDay(),
                normalizedEndDate.plusDays(1).atStartOfDay(),
                normalizedBucketWidth,
                normalizedGroupBy,
                totalInputTokens,
                totalCachedInputTokens,
                totalOutputTokens,
                totalRequests,
                buckets
        );
    }

    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isAdminKeyConfigured() {
        return adminKey != null && !adminKey.isBlank();
    }

    public String getEffectiveKeyType() {
        String effectiveKey = getEffectiveAdminKey();
        if (effectiveKey == null || effectiveKey.isBlank()) {
            return null;
        }

        if (effectiveKey.startsWith("sk-admin-")) {
            return KEY_TYPE_ADMIN;
        }

        if (effectiveKey.startsWith("sk-proj-")) {
            return KEY_TYPE_PROJECT;
        }

        return KEY_TYPE_UNKNOWN;
    }

    public String getKeyLabel() {
        return maskKey(getEffectiveAdminKey());
    }

    public String getOrganizationApiUnavailableReason() {
        String effectiveKey = getEffectiveAdminKey();
        if (effectiveKey == null || effectiveKey.isBlank()) {
            return "OpenAI Admin API Key가 설정되어 있지 않습니다.";
        }

        if (effectiveKey.startsWith("sk-proj-")) {
            return "현재 설정된 키는 project key로 보입니다. 조직 사용량/비용 API 조회에는 Admin API Key가 필요합니다.";
        }

        if (!effectiveKey.startsWith("sk-admin-")) {
            return "조직 사용량/비용 API 조회에는 OpenAI Admin API Key가 필요합니다.";
        }

        return null;
    }

    private JsonNode fetchJson(String requestUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + getEffectiveAdminKey())
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new OpenAiAdminClientException("OpenAI Admin API 권한이 없습니다. Admin API Key를 확인해주세요.");
            }
            if (response.statusCode() >= 400) {
                throw new OpenAiAdminClientException("OpenAI Admin API 호출에 실패했습니다. status=" + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new OpenAiAdminClientException("OpenAI Admin API 응답을 해석하지 못했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiAdminClientException("OpenAI Admin API 호출이 중단되었습니다.", exception);
        }
    }

    private void validateOrganizationApiKey() {
        String unavailableReason = getOrganizationApiUnavailableReason();
        if (unavailableReason != null) {
            throw new OpenAiAdminClientException(unavailableReason);
        }
    }

    private String getEffectiveAdminKey() {
        if (adminKey != null && !adminKey.isBlank()) {
            return adminKey;
        }
        return apiKey;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new OpenAiAdminClientException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private String normalizeBucketWidth(String bucketWidth) {
        if (bucketWidth == null || bucketWidth.isBlank()) {
            return "1d";
        }

        if (!List.of("1m", "1h", "1d").contains(bucketWidth)) {
            throw new OpenAiAdminClientException("bucketWidth는 1m, 1h, 1d만 지원합니다.");
        }

        return bucketWidth;
    }

    private int determineUsageLimit(LocalDate startDate, LocalDate endDate, String bucketWidth) {
        long minutes = java.time.Duration.between(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()).toMinutes();
        return switch (bucketWidth) {
            case "1m" -> (int) Math.min(Math.max(minutes, 1), 1440);
            case "1h" -> (int) Math.min(Math.max(minutes / 60, 1), 168);
            default -> (int) Math.min(Math.max(endDate.toEpochDay() - startDate.toEpochDay() + 1, 1), 31);
        };
    }

    private String buildCostsUrl(long startTime, long endTime, List<String> groupBy, int limit) {
        StringBuilder builder = new StringBuilder(COSTS_ENDPOINT)
                .append("?start_time=").append(startTime)
                .append("&end_time=").append(endTime)
                .append("&bucket_width=1d")
                .append("&limit=").append(limit);

        appendGroupBy(builder, groupBy);
        return builder.toString();
    }

    private String buildUsageUrl(long startTime, long endTime, String bucketWidth, List<String> groupBy, int limit) {
        StringBuilder builder = new StringBuilder(COMPLETIONS_USAGE_ENDPOINT)
                .append("?start_time=").append(startTime)
                .append("&end_time=").append(endTime)
                .append("&bucket_width=").append(urlEncode(bucketWidth))
                .append("&limit=").append(limit);

        appendGroupBy(builder, groupBy);
        return builder.toString();
    }

    private void appendGroupBy(StringBuilder builder, List<String> groupBy) {
        for (String group : groupBy) {
            if (group != null && !group.isBlank()) {
                builder.append("&group_by=").append(urlEncode(group.trim()));
            }
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private LocalDateTime toUtcDateTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private String textOrNull(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private String maskKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }

        if (rawKey.length() <= 8) {
            return "****";
        }

        return rawKey.substring(0, 3) + "..." + rawKey.substring(rawKey.length() - 4);
    }

    public record OpenAiCostSummary(
            boolean available,
            BigDecimal todayCostUsd,
            BigDecimal monthToDateCostUsd,
            String unavailableReason
    ) {
        public static OpenAiCostSummary available(BigDecimal todayCostUsd, BigDecimal monthToDateCostUsd) {
            return new OpenAiCostSummary(true, todayCostUsd, monthToDateCostUsd, null);
        }

        public static OpenAiCostSummary unavailable(String unavailableReason) {
            return new OpenAiCostSummary(false, null, null, unavailableReason);
        }
    }

    public static class OpenAiAdminClientException extends RuntimeException {
        public OpenAiAdminClientException(String message) {
            super(message);
        }

        public OpenAiAdminClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
