package com.miso.blog.git.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.admin.dto.OpenAiEstimateResponse;
import com.miso.blog.admin.service.OpenAiCostEstimator;
import com.miso.blog.ai.entity.AiUsageLogEntity;
import com.miso.blog.ai.repository.AiUsageLogRepository;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.common.security.SecretMaskingService;
import com.miso.blog.git.dto.OpenAiGitAnalysisResult;
import com.miso.blog.git.dto.TopicCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiGitAnalysisClient {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final ObjectMapper objectMapper;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final OpenAiCostEstimator openAiCostEstimator;
    private final SecretMaskingService secretMaskingService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${blog.ai.api-key:}")
    private String apiKey;

    @Value("${blog.ai.model:gpt-4.1-mini}")
    private String model;

    public OpenAiGitAnalysisResult analyze(String repositoryFullName, String branch, String focus, String sourceSummary) {
        validateApiKey();
        LocalDateTime startedAt = LocalDateTime.now();
        String maskedSourceSummary = secretMaskingService.mask(sourceSummary);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                    Map.of(
                            "role",
                            "system",
                            "content",
                            """
                            너는 개발자의 실제 구현 기록을 기술 블로그 글감으로 발굴하는 한국어 테크 에디터다.
                            반드시 JSON 객체로만 응답한다.
                            응답 필드는 analysisSummary, keywords, topicCandidates, recommendedTitle, draftMarkdown 이다.
                            keywords는 구체적인 기술 키워드 배열이다.
                            topicCandidates는 5개 이상 10개 이하의 객체 배열이며 각 객체는 title, angle, reason, sourceFiles, tags를 가진다.
                            draftMarkdown은 가장 좋은 글감 1개를 골라 구체적인 개발 블로그 초안으로 작성한다.
                            단순 홍보문이 아니라 문제 배경, 구현 선택, 핵심 코드 흐름, 트러블 포인트, 배운 점이 드러나야 한다.
                            확인되지 않은 사실은 단정하지 말고, 제공된 commit message와 patch 근거 안에서만 설명한다.
                            """
                    ),
                    Map.of(
                            "role",
                            "user",
                            "content",
                            buildPrompt(repositoryFullName, branch, focus, maskedSourceSummary)
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_COMPLETIONS_ENDPOINT))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_HTTP_" + response.statusCode());
                throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 분석 호출에 실패했습니다. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);

            long inputTokens = root.path("usage").path("prompt_tokens").asLong(0);
            long cachedInputTokens = root.path("usage").path("prompt_tokens_details").path("cached_tokens").asLong(0);
            long outputTokens = root.path("usage").path("completion_tokens").asLong(0);
            saveUsageLog(startedAt, LocalDateTime.now(), inputTokens, cachedInputTokens, outputTokens, true, null);

            return new OpenAiGitAnalysisResult(
                    textOrNull(result, "analysisSummary"),
                    readKeywords(result.path("keywords")),
                    readTopics(result.path("topicCandidates")),
                    textOrNull(result, "recommendedTitle"),
                    textOrNull(result, "draftMarkdown"),
                    content,
                    model,
                    inputTokens,
                    cachedInputTokens,
                    outputTokens
            );
        } catch (JsonProcessingException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_RESPONSE_PARSE_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 분석 응답을 해석하지 못했습니다.");
        } catch (IOException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_IO_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 분석 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_INTERRUPTED");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 분석이 중단되었습니다.");
        }
    }

    private String buildPrompt(String repositoryFullName, String branch, String focus, String sourceSummary) {
        return """
                저장소: %s
                브랜치: %s
                사용자가 원하는 분석 초점: %s

                아래는 최근 commit message와 파일 patch를 요약한 원본이다.
                이 개발자가 실제로 구현한 기능, 겪었을 법한 설계 판단, 트러블슈팅 포인트를 최대한 많이 찾아라.
                단, private code를 그대로 길게 복사하지 말고 블로그 독자가 이해할 수 있는 설명으로 재구성하라.

                %s
                """.formatted(
                repositoryFullName,
                branch,
                focus == null || focus.isBlank() ? "(특별한 초점 없음)" : focus,
                sourceSummary
        );
    }

    private void saveUsageLog(
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            boolean success,
            String errorCode
    ) {
        aiUsageLogRepository.save(AiUsageLogEntity.builder()
                .provider("openai")
                .model(model)
                .inputTokens(inputTokens)
                .cachedInputTokens(cachedInputTokens)
                .outputTokens(outputTokens)
                .estimatedCostUsd(estimateCost(inputTokens, cachedInputTokens, outputTokens))
                .success(success)
                .errorCode(errorCode)
                .requestStartedAt(startedAt)
                .requestFinishedAt(finishedAt)
                .build());
    }

    private BigDecimal estimateCost(long inputTokens, long cachedInputTokens, long outputTokens) {
        try {
            OpenAiEstimateResponse response = openAiCostEstimator.estimate(model, inputTokens, cachedInputTokens, outputTokens);
            return response.estimatedCostUsd();
        } catch (Exception exception) {
            return null;
        }
    }

    private List<String> readKeywords(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        return objectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    private List<TopicCandidateResponse> readTopics(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        return objectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    private String textOrNull(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI API Key가 설정되어 있지 않습니다.");
        }
    }
}
