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
import com.miso.blog.post.code.GeneralBlogLength;
import com.miso.blog.post.dto.GeneratedBlogDraft;
import com.miso.blog.post.dto.ReviseBlogPostWithAiRequest;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.service.BlogReferenceContextService;
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
public class OpenAiBlogRevisionComposer {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final ObjectMapper objectMapper;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final OpenAiCostEstimator openAiCostEstimator;
    private final SecretMaskingService secretMaskingService;
    private final BlogPostMemoryContextService blogPostMemoryContextService;
    private final BlogReferenceContextService blogReferenceContextService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${blog.ai.api-key:}")
    private String apiKey;

    @Value("${blog.ai.model:gpt-4.1-mini}")
    private String model;

    public GeneratedBlogDraft revise(BlogPostEntity blogPost, List<String> currentTags, ReviseBlogPostWithAiRequest request) {
        validateApiKey();
        LocalDateTime startedAt = LocalDateTime.now();

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
                                    너는 한국어 블로그 글을 사용자의 추가 요청에 맞춰 다듬는 전문 에디터다.
                                    개발 블로그와 일반 블로그를 모두 다룬다.
                                    반드시 JSON 객체로만 응답한다.
                                    응답 필드는 title, summary, contentMarkdown, tags 이다.
                                     기존 글의 핵심 사실과 사용자가 제공한 근거를 유지한다.
                                     사용자가 새로 요청한 수정사항을 우선 반영하되, 없는 사실은 추가하지 않는다.
                                     Markdown 구조를 유지하고 읽기 좋은 제목, 소제목, 문단으로 재작성한다.
                                     일반 블로그는 네이버 블로그에 붙여넣기 좋게 간결하고 고유한 제목, 짧은 문단, 자연스러운 키워드, 구체적인 사진 설명을 우선한다.
                                     네이버 검색 노출만을 위한 키워드 반복, 숨겨진 키워드 나열, 무관한 인기 키워드 끼워넣기는 하지 않는다.
                                     실제 URL, 전화번호, 가격, 메뉴, 영업시간이 제공되지 않았다면 임시값이나 예시값을 만들지 않는다.
                                     `#`, `example.com`, `02-0000-0000` 같은 placeholder 정보는 본문에 넣지 않는다.
                                     Reference URLs에 실제 본문 발췌가 있으면 현재 글을 더 자연스럽게 고치는 참고 자료로 쓰되, 긴 문장을 그대로 베끼지 않는다.
                                     """
                            ),
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    buildPrompt(blogPost, currentTags, request)
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
                throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 수정 호출에 실패했습니다. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);
            long inputTokens = root.path("usage").path("prompt_tokens").asLong(0);
            long cachedInputTokens = root.path("usage").path("prompt_tokens_details").path("cached_tokens").asLong(0);
            long outputTokens = root.path("usage").path("completion_tokens").asLong(0);
            saveUsageLog(startedAt, LocalDateTime.now(), inputTokens, cachedInputTokens, outputTokens, true, null);

            return new GeneratedBlogDraft(
                    textOrDefault(result, "title", blogPost.getTitle()),
                    textOrDefault(result, "summary", blogPost.getSummary()),
                    textOrDefault(result, "contentMarkdown", blogPost.getContentMarkdown()),
                    readTags(result.path("tags"), currentTags),
                    content,
                    model
            );
        } catch (JsonProcessingException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_RESPONSE_PARSE_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 수정 응답을 해석하지 못했습니다.");
        } catch (IOException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_IO_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 수정 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_INTERRUPTED");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 블로그 수정이 중단되었습니다.");
        }
    }

    private String buildPrompt(BlogPostEntity blogPost, List<String> currentTags, ReviseBlogPostWithAiRequest request) {
        return """
                현재 제목: %s
                현재 요약: %s
                현재 태그: %s
                생성/출처 메모: %s
                유지 옵션:
                - 제목 유지: %s
                - 태그 유지: %s

                추가 수정 요청:
                %s

                추가 메모:
                %s

                원하는 톤: %s
                목표 길이: %s

                이전 저장 글 참고:
                %s

                작성 기준:
                - 생성/출처 메모가 "일반 블로그 AI 작성 결과"이거나 내용상 맛집/카페/여행/제품/일상 후기라면 네이버 블로그용 글로 다듬는다.
                - 일반 블로그 제목은 장소/대상명, 핵심 경험, 검색 의도를 과하지 않게 담고 낚시성 표현을 피한다.
                - 일반 블로그 본문은 모바일에서 읽기 좋게 문단을 짧게 나누고, 같은 키워드를 억지로 반복하지 않는다.
                - 사진 URL이나 사진 자리표시가 있으면 장면/메뉴/공간이 드러나는 설명을 유지하거나 보완한다.
                - 개발 블로그라면 기술 근거와 구현 맥락을 우선하고, 네이버 후기체로 바꾸지 않는다.

                현재 Markdown:
                %s
                """.formatted(
                blogPost.getTitle(),
                valueOrDefault(blogPost.getSummary(), "(없음)"),
                writeJsonQuietly(currentTags),
                valueOrDefault(blogPost.getSourceNote(), "(없음)"),
                Boolean.TRUE.equals(request.preserveTitle()),
                Boolean.TRUE.equals(request.preserveTags()),
                secretMaskingService.mask(request.revisionInstruction()),
                secretMaskingService.mask(valueOrDefault(request.additionalMemo(), "(없음)")),
                valueOrDefault(request.tone(), "(기존 글 톤 유지)"),
                request.targetLength() == null ? GeneralBlogLength.MEDIUM : request.targetLength(),
                blogPostMemoryContextService.buildRecentPostContext(blogPost.getId())
                        + "\n\nReference URLs:\n"
                        + blogReferenceContextService.buildReferenceContext(BlogReferenceType.DEVELOPMENT, BlogReferenceType.GENERAL),
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

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI API Key가 설정되어 있지 않습니다.");
        }
    }
}
