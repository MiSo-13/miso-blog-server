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
import com.miso.blog.git.dto.TopicCandidateResponse;
import com.miso.blog.post.dto.CreateBlogPostFromAnalysisRequest;
import com.miso.blog.post.dto.GeneratedBlogDraft;
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
public class OpenAiBlogDraftComposer {
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

    public GeneratedBlogDraft compose(
            String repositoryName,
            String analysisSummary,
            String sourceSummary,
            List<String> keywords,
            List<TopicCandidateResponse> topicCandidates,
            CreateBlogPostFromAnalysisRequest request
    ) {
        validateApiKey();
        LocalDateTime startedAt = LocalDateTime.now();
        String maskedSourceSummary = secretMaskingService.mask(sourceSummary);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of(
                                    "role",
                                    "system",
                                    "content",
                                    """
                                    너는 실제 구현 기록을 바탕으로 개발 블로그 초안을 쓰는 한국어 테크 라이터다.
                                    반드시 JSON 객체로만 응답한다.
                                    응답 필드는 title, summary, contentMarkdown, tags 이다.
                                    contentMarkdown은 Markdown 본문이며, 문제 배경, 구현 선택, 코드 흐름, 트러블슈팅 포인트, 배운 점을 포함한다.
                                    제공된 source summary 밖의 사실은 단정하지 않는다.
                                    코드 전체를 길게 복사하지 말고, 필요한 경우 파일 경로와 흐름 중심으로 설명한다.
                                    """
                            ),
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    buildPrompt(repositoryName, analysisSummary, maskedSourceSummary, keywords, topicCandidates, request)
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
                throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 작성 호출에 실패했습니다. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);
            long inputTokens = root.path("usage").path("prompt_tokens").asLong(0);
            long cachedInputTokens = root.path("usage").path("prompt_tokens_details").path("cached_tokens").asLong(0);
            long outputTokens = root.path("usage").path("completion_tokens").asLong(0);
            saveUsageLog(startedAt, LocalDateTime.now(), inputTokens, cachedInputTokens, outputTokens, true, null);

            return new GeneratedBlogDraft(
                    textOrDefault(result, "title", repositoryName + " 구현 기록 정리"),
                    textOrDefault(result, "summary", analysisSummary),
                    textOrDefault(result, "contentMarkdown", "# " + repositoryName + " 구현 기록 정리"),
                    readTags(result.path("tags"), keywords),
                    content,
                    model
            );
        } catch (JsonProcessingException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_RESPONSE_PARSE_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 작성 응답을 해석하지 못했습니다.");
        } catch (IOException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_IO_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 작성 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_INTERRUPTED");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 작성이 중단되었습니다.");
        }
    }

    private String buildPrompt(
            String repositoryName,
            String analysisSummary,
            String sourceSummary,
            List<String> keywords,
            List<TopicCandidateResponse> topicCandidates,
            CreateBlogPostFromAnalysisRequest request
    ) {
        return """
                저장소/프로젝트: %s
                선택 키워드: %s
                선택 주제: %s
                작성 초점: %s
                독자: %s

                분석 요약:
                %s

                글감 후보:
                %s

                분석 근거:
                %s
                """.formatted(
                repositoryName,
                keywords == null || keywords.isEmpty() ? "(선택 없음)" : String.join(", ", keywords),
                request.selectedTopicTitle() == null || request.selectedTopicTitle().isBlank() ? "(자동 선택)" : request.selectedTopicTitle(),
                request.writingFocus() == null || request.writingFocus().isBlank() ? "(구현 흐름과 트러블슈팅 중심)" : request.writingFocus(),
                request.audience() == null || request.audience().isBlank() ? "비슷한 문제를 겪는 백엔드/풀스택 개발자" : request.audience(),
                analysisSummary == null ? "(없음)" : analysisSummary,
                writeJsonQuietly(topicCandidates),
                sourceSummary == null ? "(없음)" : sourceSummary
        );
    }

    private List<String> readTags(JsonNode node, List<String> fallback) {
        if (node != null && node.isArray()) {
            return objectMapper.convertValue(node, new TypeReference<>() {
            });
        }
        return fallback == null ? List.of() : fallback;
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

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI API Key가 설정되어 있지 않습니다.");
        }
    }
}
