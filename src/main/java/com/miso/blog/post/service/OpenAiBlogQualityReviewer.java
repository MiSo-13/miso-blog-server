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
import com.miso.blog.naver.service.NaverBlogTrendContextService;
import com.miso.blog.post.dto.BlogPostQualityReviewRequest;
import com.miso.blog.post.dto.BlogPostQualityReviewResponse;
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
public class OpenAiBlogQualityReviewer {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final ObjectMapper objectMapper;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final OpenAiCostEstimator openAiCostEstimator;
    private final SecretMaskingService secretMaskingService;
    private final BlogPostMemoryContextService blogPostMemoryContextService;
    private final BlogReferenceContextService blogReferenceContextService;
    private final NaverBlogTrendContextService naverBlogTrendContextService;
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
                                    너는 한국어 블로그 편집장, 네이버 블로그 SEO 리뷰어, 수익화 전략 리뷰어다.
                                     글이 AI가 쓴 것처럼 보이는지, 입력 근거 없이 단정한 문장이 있는지,
                                    네이버 블로그 검색 유입과 수익화 준비가 되었는지 냉정하게 평가한다.
                                     반드시 JSON 객체로만 응답한다.

                                     응답 필드:
                                     verdict, humanNaturalnessScore, factualGroundingScore, readabilityScore,
                                     seoReadinessScore, monetizationReadinessScore, publishReady,
                                     strengths, issues, unsupportedClaims, aiLikePhrases,
                                     monetizationSuggestions, referenceFeedback, referenceSentenceSuggestions,
                                     naverBlogFeedback, naverBlogTitleSuggestions, naverBlogStructureSuggestions,
                                     naverTrendFeedback, naverTrendTitlePatterns, naverTrendStructurePatterns,
                                     revisionInstruction

                                     점수는 0~100 정수다. publishReady는 사람 검토 없이 발행해도 안전한 수준일 때만 true다.
                                     근거 없는 주장처럼 보이는 문장은 unsupportedClaims에 구체적으로 적는다.
                                     referenceFeedback에는 저장된 레퍼런스의 실제 본문 발췌와 현재 글을 비교해,
                                     잘 반영된 점, 놓친 내용, 과하게 베낀 듯한 표현, 부정확한 단정을 구체적으로 적는다.
                                     referenceSentenceSuggestions에는 레퍼런스에서 배울 만한 문장 구조나 표현 방향을 적되,
                                     긴 문장을 그대로 복사하지 말고 짧은 표현 조각이나 작성 전략으로만 제안한다.
                                     naverBlogFeedback에는 네이버 블로그에 맞는 제목, 도입부, 문단 길이, 키워드 자연스러움,
                                     사진 설명, 독자 도움성, 복사/중복 느낌 여부를 구체적으로 평가한다.
                                     naverBlogTitleSuggestions에는 네이버 블로그용 제목 후보를 2~4개 제안한다.
                                     naverBlogStructureSuggestions에는 네이버 블로그에서 읽기 좋은 소제목 순서와 보완 섹션을 제안한다.
                                     naverTrendFeedback에는 네이버 블로그 상위 글 참고 자료와 현재 글을 비교해,
                                     현재 글이 놓친 검색 의도, 정보 배치, 도입부/사진 흐름, 과도한 모방 위험을 구체적으로 적는다.
                                     naverTrendTitlePatterns에는 상위 글에서 관찰한 제목 패턴을 그대로 베끼지 않는 전략으로 정리한다.
                                     naverTrendStructurePatterns에는 상위 글에서 관찰한 구조/사진/정보 배치 패턴을 작성 전략으로 정리한다.
                                     일반 블로그가 아닌 개발 블로그라면 naverBlog 계열 필드는 빈 배열로 두거나, 제목/구조 수준의 최소 제안만 한다.
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
                    readStringList(result.path("referenceFeedback")),
                    readStringList(result.path("referenceSentenceSuggestions")),
                    readStringList(result.path("naverBlogFeedback")),
                    readStringList(result.path("naverBlogTitleSuggestions")),
                    readStringList(result.path("naverBlogStructureSuggestions")),
                    readStringList(result.path("naverTrendFeedback")),
                    readStringList(result.path("naverTrendTitlePatterns")),
                    readStringList(result.path("naverTrendStructurePatterns")),
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
                생성/출처 메모: %s

                원본 입력/메모:
                %s

                이전 저장 글 참고:
                %s

                네이버 블로그 상위 글 참고:
                %s

                 리뷰 기준:
                 - AI가 쓴 듯한 일반론, 과장된 칭찬, 반복 표현을 찾는다.
                 - 개발 블로그는 실제 구현 근거가 빈약한 기술 설명과 추상적인 결론을 찾는다.
                 - 일반 블로그는 입력에 없는 메뉴, 가격, 대기, 예약, 재료, 영업 정보, 방문 전 확인 과정 같은 추정 문장을 찾는다.
                 - 수익화 관점에서는 검색 의도, 체류 시간, 이미지 배치, 내부 링크와 제휴 링크 후보, 독자 행동 유도 가능성을 본다.
                 - 광고성 과장보다 신뢰가 있는 개인 경험 문체를 우선한다.
                  - 일반 블로그는 네이버 블로그에 올릴 글로 보고, 검색 유입 독자에게 도움이 되는 정보와 개인 관찰이 균형 있는지 본다.
                  - 생성/출처 메모가 "일반 블로그 AI 작성 결과"이거나 내용상 맛집/카페/여행/제품/일상 후기라면 네이버 블로그 기준을 우선 적용한다.
                  - 제목과 요약은 간결하고 고유한지, 키워드가 자연스럽게 들어갔는지, 낚시성/과도한 반복이 없는지 본다.
                 - 네이버 블로그 모바일 독자를 고려해 문단이 너무 길지 않은지, 소제목 흐름이 검색 의도와 맞는지 본다.
                 - 사진 또는 이미지 자리표시자가 있으면 장면을 설명하는 대체 문구가 충분한지 본다.
                 - 검색 노출만을 위한 키워드 남용, 무관한 인기 키워드, 복사한 듯한 레퍼런스 표현은 문제로 지적한다.
                 - 네이버 블로그 상위 글 참고 자료와 현재 글을 비교해 제목, 도입부, 소제목, 사진 설명, 정보 밀도 차이를 피드백한다.
                 - 상위 글의 원문 문장이나 경험을 베끼는 방향은 제안하지 않고, 패턴과 전략만 제안한다.
                 - Reference URLs의 실제 본문 발췌를 현재 글과 비교해 어떤 표현/구조를 배울지, 어떤 문장은 근거가 약한지 세심하게 피드백한다.
                 - 레퍼런스 문장을 길게 그대로 복사하라고 지시하지 않는다. 자연스러운 문장 구조와 관찰 포인트만 제안한다.

                 Markdown 본문:
                 %s
                """.formatted(
                blogPost.getTitle(),
                valueOrDefault(blogPost.getSummary(), "(없음)"),
                writeJsonQuietly(tags),
                valueOrDefault(request == null ? null : request.targetReader(), "(지정 없음)"),
                valueOrDefault(request == null ? null : request.monetizationGoal(), "검색 유입과 장기 수익화"),
                valueOrDefault(blogPost.getSourceNote(), "(없음)"),
                secretMaskingService.mask(valueOrDefault(request == null ? null : request.originalInputMemo(), "(없음)")),
                blogPostMemoryContextService.buildRecentPostContext(blogPost.getId())
                        + "\n\nReference URLs:\n"
                        + blogReferenceContextService.buildReferenceContext(BlogReferenceType.DEVELOPMENT, BlogReferenceType.GENERAL),
                naverBlogTrendContextService.buildTrendContext(blogPost, tags),
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
