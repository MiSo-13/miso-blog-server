package com.miso.blog.post.service;

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
import com.miso.blog.post.dto.BlogPostQualityReviewRequest;
import com.miso.blog.post.dto.BlogPostQualityReviewResponse;
import com.miso.blog.post.entity.BlogPostEntity;
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
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiBlogQualityReviewer {
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

    public BlogPostQualityReviewResponse review(BlogPostEntity blogPost, List<String> tags, BlogPostQualityReviewRequest request) {
        validateApiKey();
        LocalDateTime startedAt = LocalDateTime.now();

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0.15,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of(
                                    "role",
                                    "system",
                                    "content",
                                    """
                                    너는 한국어 블로그 편집장, SEO 리뷰어, 수익화 전략 리뷰어다.
                                    글이 AI가 쓴 것처럼 보이는지, 입력 근거 없이 단정한 문장이 있는지,
                                    검색 유입과 수익화 준비가 되었는지 냉정하게 평가한다.
                                    반드시 JSON 객체로만 응답한다.

                                    응답 필드:
                                    verdict, humanNaturalnessScore, factualGroundingScore, readabilityScore,
                                    seoReadinessScore, monetizationReadinessScore, publishReady,
                                    strengths, issues, unsupportedClaims, aiLikePhrases,
                                    monetizationSuggestions, revisionInstruction

                                    점수는 0~100 정수다. publishReady는 사람 검토 없이 발행해도 안전한 수준일 때만 true다.
                                    근거 없는 주장처럼 보이는 문장은 unsupportedClaims에 구체적으로 적는다.
                                    revisionInstruction은 POST /revise/ai에 바로 넣을 수 있는 한국어 수정 지시문으로 작성한다.
                                    """
                            ),
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    buildPrompt(blogPost, tags, request)
                            )
                    )
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_COMPLETIONS_ENDPOINT))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_HTTP_" + response.statusCode());
                throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 품질 리뷰 호출에 실패했습니다. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);
            long inputTokens = root.path("usage").path("prompt_tokens").asLong(0);
            long cachedInputTokens = root.path("usage").path("prompt_tokens_details").path("cached_tokens").asLong(0);
            long outputTokens = root.path("usage").path("completion_tokens").asLong(0);
            saveUsageLog(startedAt, LocalDateTime.now(), inputTokens, cachedInputTokens, outputTokens, true, null);

            return new BlogPostQualityReviewResponse(
                    blogPost.getId(),
                    textOrDefault(result, "verdict", "리뷰 결과를 확인하세요."),
                    intOrDefault(result, "humanNaturalnessScore"),
                    intOrDefault(result, "factualGroundingScore"),
                    intOrDefault(result, "readabilityScore"),
                    intOrDefault(result, "seoReadinessScore"),
                    intOrDefault(result, "monetizationReadinessScore"),
                    result.path("publishReady").asBoolean(false),
                    readStringList(result.path("strengths")),
                    readStringList(result.path("issues")),
                    readStringList(result.path("unsupportedClaims")),
                    readStringList(result.path("aiLikePhrases")),
                    readStringList(result.path("monetizationSuggestions")),
                    textOrDefault(result, "revisionInstruction", ""),
                    content,
                    model
            );
        } catch (JsonProcessingException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_RESPONSE_PARSE_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 품질 리뷰 응답을 해석하지 못했습니다.");
        } catch (IOException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_IO_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 품질 리뷰 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_INTERRUPTED");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 품질 리뷰가 중단되었습니다.");
        }
    }

    private String buildPrompt(BlogPostEntity blogPost, List<String> tags, BlogPostQualityReviewRequest request) {
        return """
                제목: %s
                요약: %s
                태그: %s
                목표 독자: %s
                수익화 목표: %s

                원본 입력/메모:
                %s

                리뷰 기준:
                - AI가 쓴 듯한 일반론, 과장된 칭찬, 반복 표현을 찾는다.
                - 개발 블로그는 실제 구현 근거가 빈약한 기술 설명과 추상적인 결론을 찾는다.
                - 일반 블로그는 입력에 없는 메뉴, 가격, 대기, 예약, 재료, 영업 정보, 방문 전 확인 과정 같은 추정 문장을 찾는다.
                - 수익화 관점에서는 검색 의도, 체류 시간, 이미지 배치, 내부 링크와 제휴 링크 후보, 독자 행동 유도 가능성을 본다.
                - 광고성 과장보다 신뢰가 있는 개인 경험 문체를 우선한다.

                Markdown 본문:
                %s
                """.formatted(
                blogPost.getTitle(),
                valueOrDefault(blogPost.getSummary(), "(없음)"),
                writeJsonQuietly(tags),
                valueOrDefault(request == null ? null : request.targetReader(), "(지정 없음)"),
                valueOrDefault(request == null ? null : request.monetizationGoal(), "검색 유입과 장기 수익화"),
                secretMaskingService.mask(valueOrDefault(request == null ? null : request.originalInputMemo(), "(없음)")),
                secretMaskingService.mask(blogPost.getContentMarkdown())
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

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    private int intOrDefault(JsonNode node, String fieldName) {
        return Math.max(0, Math.min(100, node.path(fieldName).asInt(0)));
    }

    private String textOrDefault(JsonNode node, String fieldName, String fallback) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String writeJsonQuietly(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI API Key가 설정되어 있지 않습니다.");
        }
    }
}
