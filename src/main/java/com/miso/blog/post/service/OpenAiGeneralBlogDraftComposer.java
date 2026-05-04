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
import com.miso.blog.post.code.GeneralBlogLength;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.dto.GeneratedBlogDraft;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiGeneralBlogDraftComposer {
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

    public GeneratedBlogDraft compose(CreateGeneralBlogPostRequest request) {
        validateApiKey();
        LocalDateTime startedAt = LocalDateTime.now();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.35);
            requestBody.put("max_tokens", maxOutputTokens(request.targetLength()));
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                            Map.of(
                                    "role",
                                    "system",
                                    "content",
                                    """
                                    너는 네이버 블로그에 올릴 한국어 일반 블로그 글을 쓰는 전문 에디터다.
                                    맛집, 카페, 여행, 제품 리뷰, 일상 글을 네이버 블로그 독자가 읽기 좋은 자연스러운 후기체로 작성한다.
                                     반드시 JSON 객체로만 응답한다.
                                     응답 필드는 title, summary, contentMarkdown, tags 이다.
                                     title은 네이버 검색 결과에서 주제가 바로 드러나도록 간결하고 고유하게 쓴다.
                                     summary는 네이버 검색 스니펫처럼 글의 핵심을 2~3문장으로 요약한다.
                                     contentMarkdown은 네이버 블로그에 복사하기 좋은 Markdown 초안이어야 하며, H2 소제목과 짧은 문단을 포함한다.
                                     contentMarkdown 첫 줄에 제목을 #으로 다시 반복하지 말고, 자연스러운 도입 문단이나 ## 소제목으로 시작한다.
                                     네이버 블로그 상위 글 참고 자료가 있으면 제목 패턴, 도입부, 소제목 흐름, 사진/정보 배치만 참고한다.
                                     사용자가 요청한 목표 길이를 반드시 지킨다. LONG은 짧은 요약문이 아니라 충분히 확장된 본문이어야 한다.
                                     사용자가 준 메모, 필수 문구, 키워드, 사진 설명을 최우선 근거로 삼는다.
                                     모르는 사실, 방문하지 않은 경험, 가격, 영업시간, 메뉴, 위치 정보는 단정하지 않는다.
                                     사진 URL이 있으면 본문 흐름에 맞춰 Markdown 이미지 문법으로 배치한다.
                                     과장 광고처럼 쓰지 말고, 개인 블로그 후기처럼 구체적이고 담백하게 쓴다.
                                     제공되지 않은 메뉴 구성, 재료, 양, 대기 시간, 영업 정보, 가격 상세는 절대 만들어내지 않는다.
                                     사용자의 메모에 없는 방문 전 검색 과정, 예약 여부, 다른 메뉴 평가, 피크 시간 상황도 만들어내지 않는다.
                                    네이버 검색 노출만을 위한 키워드 반복, 숨겨진 키워드 나열, 무관한 실시간 이슈 끼워넣기는 하지 않는다.
                                    검색엔진보다 실제 독자에게 도움이 되는 정보와 개인적인 관찰을 우선한다.
                                     """
                            ),
                            Map.of(
                                    "role",
                                    "user",
                                    "content",
                                    buildPrompt(request)
                            )
                    ));

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
                throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 일반 블로그 작성 호출에 실패했습니다. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);
            long inputTokens = root.path("usage").path("prompt_tokens").asLong(0);
            long cachedInputTokens = root.path("usage").path("prompt_tokens_details").path("cached_tokens").asLong(0);
            long outputTokens = root.path("usage").path("completion_tokens").asLong(0);
            saveUsageLog(startedAt, LocalDateTime.now(), inputTokens, cachedInputTokens, outputTokens, true, null);

            return new GeneratedBlogDraft(
                    textOrDefault(result, "title", defaultTitle(request)),
                    textOrDefault(result, "summary", defaultSummary(request)),
                    textOrDefault(result, "contentMarkdown", defaultContentMarkdown(request)),
                    readTags(result.path("tags"), request.keywords()),
                    content,
                    model
            );
        } catch (JsonProcessingException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_RESPONSE_PARSE_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 일반 블로그 작성 응답을 해석하지 못했습니다.");
        } catch (IOException exception) {
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_IO_ERROR");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 일반 블로그 작성 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveUsageLog(startedAt, LocalDateTime.now(), 0, 0, 0, false, "OPENAI_INTERRUPTED");
            throw new GeneralException(ErrorCode.BAD_REQUEST, "OpenAI 일반 블로그 작성이 중단되었습니다.");
        }
    }

    private String buildPrompt(CreateGeneralBlogPostRequest request) {
        return """
                카테고리: %s
                제목 힌트: %s
                장소/대상명: %s
                주소/지역 힌트: %s
                필수 포함 문구: %s
                키워드: %s
                톤: %s
                독자: %s
                목표 길이: %s
                분량 요구: %s
                사진/이미지 자료: %s
                이미지 배치 메모: %s

                이전 저장 글 참고:
                %s

                네이버 블로그 상위 글 참고:
                %s

                사용자 메모:
                %s

                 작성 지침:
                 - 첫 문단은 검색 유입 독자가 바로 맥락을 이해할 수 있게 쓴다.
                 - 첫 문단에 핵심 키워드를 1회 자연스럽게 포함하되, 억지 반복은 하지 않는다.
                 - 네이버 블로그 모바일 화면에서 읽기 좋게 한 문단은 2~4문장 안팎으로 짧게 나눈다.
                  - 제목은 장소/대상명, 핵심 경험, 검색 의도를 과하지 않게 담고 낚시성 표현을 피한다.
                  - 요약은 네이버 검색 스니펫처럼 누가, 어디서/무엇을, 어떤 점을 확인할 수 있는지 분명히 적는다.
                  - 본문에서 제목을 다시 큰 제목으로 반복하지 말고, 네이버 블로그 본문에 붙여넣기 좋은 흐름을 만든다.
                 - 필수 포함 문구는 의미를 바꾸지 말고 자연스럽게 녹인다.
                 - 사진 URL이 있으면 본문 중간에 ![사진 설명](사진 URL) 형태로 넣고, 필요하면 짧은 캡션을 붙인다.
                 - 사진 URL이 없고 설명만 있으면 [사진: 설명] 형태의 자리표시자를 넣는다.
                 - 사진 설명은 “사진” 같은 빈 설명보다 장면/메뉴/공간이 드러나게 쓴다.
                 - 제공된 사진 URL은 모두 한 번씩 사용한다.
                 - RESTAURANT/CAFE 글은 방문 배경, 공간 분위기, 메뉴/맛, 좋았던 점, 아쉬운 점, 추천 대상, 마무리를 자연스럽게 포함한다.
                 - TRAVEL 글은 이동/동선, 인상적인 장면, 좋았던 점, 아쉬운 점, 추천 대상을 포함한다.
                 - PRODUCT_REVIEW 글은 사용 배경, 장점, 아쉬운 점, 추천 대상을 포함한다.
                 - 각 주요 소제목 아래에는 최소 2문단을 작성한다.
                 - 과도한 광고 문구보다 실제 후기처럼 장단점과 분위기를 균형 있게 쓴다.
                 - 같은 키워드나 장소명을 반복해 늘어놓지 말고, 동의어와 구체적 관찰로 자연스럽게 풀어쓴다.
                 - 대표 메뉴, 메뉴 구성, 재료 신선도, 영업시간, 정확한 가격, 웨이팅, 좌석 수처럼 제공되지 않은 정보는 추가하지 않는다.
                 - “후기를 보고 방문했다”, “미리 예약했다”, “다른 메뉴도 좋았다”, “신선한 재료”처럼 입력에 없는 배경/판단은 쓰지 않는다.
                 - 분량을 늘릴 때도 새로운 사실을 만들지 말고, 제공된 메모를 더 자세한 감상/맥락/추천 포인트로 풀어쓴다.
                 - 정보가 부족한 영역은 “자세히 확인하지 못했지만”, “메모 기준으로는”처럼 한계를 드러낸다.
                 - 제공되지 않은 사실은 “그랬다”고 단정하지 않는다.
                 - Reference URLs에 실제 본문 발췌가 있으면 문장 흐름, 소제목 구조, 관찰 포인트를 참고하되 긴 문장을 그대로 베끼지 않는다.
                 - 레퍼런스에서 확인한 내용과 사용자 메모를 구분하고, 사용자 경험처럼 꾸며내지 않는다.
                 - 네이버 블로그 상위 글 참고 자료는 현재 검색 상위 노출 글의 경향을 파악하기 위한 자료다. 원문 문장이나 경험을 복사하지 않는다.
                 - 상위 글에 자주 나오는 정보라도 사용자가 제공하지 않은 가격, 메뉴, 웨이팅, 예약, 효능, 방문 경험은 현재 글의 사실처럼 쓰지 않는다.
                  """.formatted(
                request.category(),
                valueOrDefault(request.titleHint(), "(없음)"),
                valueOrDefault(request.placeName(), "(없음)"),
                valueOrDefault(request.addressHint(), "(없음)"),
                writeJsonQuietly(request.requiredPhrases()),
                writeJsonQuietly(request.keywords()),
                valueOrDefault(request.tone(), "친근하고 자연스러운 후기체"),
                valueOrDefault(request.audience(), "일반 블로그 독자"),
                request.targetLength() == null ? GeneralBlogLength.MEDIUM : request.targetLength(),
                lengthInstruction(request.targetLength()),
                writeJsonQuietly(request.photos()),
                valueOrDefault(request.imagePlacementNotes(), "(없음)"),
                blogPostMemoryContextService.buildRecentPostContext(null)
                        + "\n\nReference URLs:\n"
                        + blogReferenceContextService.buildReferenceContext(BlogReferenceType.GENERAL),
                naverBlogTrendContextService.buildTrendContext(request),
                secretMaskingService.mask(valueOrDefault(request.memo(), "(없음)"))
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

    private String defaultTitle(CreateGeneralBlogPostRequest request) {
        if (request.titleHint() != null && !request.titleHint().isBlank()) {
            return request.titleHint().trim();
        }
        if (request.placeName() != null && !request.placeName().isBlank()) {
            return request.placeName().trim() + " 방문 후기";
        }
        return request.category().name() + " 블로그 후기";
    }

    private int maxOutputTokens(GeneralBlogLength targetLength) {
        GeneralBlogLength length = targetLength == null ? GeneralBlogLength.MEDIUM : targetLength;
        return switch (length) {
            case SHORT -> 1200;
            case MEDIUM -> 2200;
            case LONG -> 4000;
        };
    }

    private String lengthInstruction(GeneralBlogLength targetLength) {
        GeneralBlogLength length = targetLength == null ? GeneralBlogLength.MEDIUM : targetLength;
        return switch (length) {
            case SHORT -> "contentMarkdown 기준 800~1200자. 핵심 후기 중심.";
            case MEDIUM -> "contentMarkdown 기준 1400~2200자. 소제목 4개 이상.";
            case LONG -> "contentMarkdown 기준 최소 2500자 이상. 소제목 6개 이상, 각 소제목마다 2문단 이상.";
        };
    }

    private String defaultSummary(CreateGeneralBlogPostRequest request) {
        if (request.memo() == null || request.memo().isBlank()) {
            return "입력한 키워드와 사진 메모를 바탕으로 생성한 블로그 초안입니다.";
        }
        String memo = request.memo().trim();
        return memo.length() <= 180 ? memo : memo.substring(0, 180);
    }

    private String defaultContentMarkdown(CreateGeneralBlogPostRequest request) {
        return """
                %s

                ## 작성 메모

                %s
                """.formatted(
                defaultSummary(request),
                secretMaskingService.mask(valueOrDefault(request.memo(), "입력한 메모를 바탕으로 본문을 다듬어 주세요."))
        );
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
