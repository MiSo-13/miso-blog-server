package com.miso.blog.post.service;

import com.miso.blog.post.code.GeneralBlogLength;
import com.miso.blog.post.dto.BlogPostQualityImproveRequest;
import com.miso.blog.post.dto.BlogPostQualityImproveResponse;
import com.miso.blog.post.dto.BlogPostQualityReviewRequest;
import com.miso.blog.post.dto.BlogPostQualityReviewResponse;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.ReviseBlogPostWithAiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogPostQualityImproveService {
    private final BlogPostQualityReviewService blogPostQualityReviewService;
    private final BlogPostRevisionService blogPostRevisionService;
    private final BlogPostService blogPostService;

    public BlogPostQualityImproveResponse improve(Long blogPostId, BlogPostQualityImproveRequest request) {
        BlogPostQualityImproveRequest effectiveRequest = request == null ? new BlogPostQualityImproveRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null
        ) : request;

        BlogPostQualityReviewRequest reviewRequest = effectiveRequest.reviewRequest();
        BlogPostQualityReviewResponse initialReview = blogPostQualityReviewService.review(blogPostId, reviewRequest);
        BlogPostQualityReviewResponse currentReview = initialReview;
        BlogPostResponse currentPost = blogPostService.getBlogPost(blogPostId);
        List<String> revisionInstructions = new ArrayList<>();

        int maxRounds = valueOrDefault(effectiveRequest.maxRevisionRounds(), 1);
        for (int round = 0; round < maxRounds && !isCriteriaPassed(currentReview, effectiveRequest); round++) {
            String revisionInstruction = buildRevisionInstruction(currentReview, effectiveRequest, round + 1);
            revisionInstructions.add(revisionInstruction);

            currentPost = blogPostRevisionService.reviseWithAi(blogPostId, new ReviseBlogPostWithAiRequest(
                    revisionInstruction,
                    effectiveRequest.additionalRevisionMemo(),
                    effectiveRequest.tone(),
                    effectiveRequest.targetLength() == null ? GeneralBlogLength.MEDIUM : effectiveRequest.targetLength(),
                    effectiveRequest.preserveTitle(),
                    effectiveRequest.preserveTags() == null ? true : effectiveRequest.preserveTags(),
                    false
            ));
            currentReview = blogPostQualityReviewService.review(blogPostId, reviewRequest);
        }

        boolean criteriaPassed = isCriteriaPassed(currentReview, effectiveRequest);
        if (criteriaPassed && Boolean.TRUE.equals(effectiveRequest.markReviewReadyWhenPassed())) {
            currentPost = blogPostService.markReviewReady(blogPostId);
        }

        return new BlogPostQualityImproveResponse(
                blogPostId,
                revisionInstructions.size(),
                criteriaPassed,
                currentReview.publishReady(),
                initialReview,
                currentReview,
                currentPost,
                revisionInstructions,
                criteriaPassed ? "품질 기준을 통과했습니다." : "최대 수정 횟수 안에서 품질 기준을 통과하지 못했습니다."
        );
    }

    private boolean isCriteriaPassed(BlogPostQualityReviewResponse review, BlogPostQualityImproveRequest request) {
        if (Boolean.TRUE.equals(request.requirePublishReady()) && !review.publishReady()) {
            return false;
        }
        if (hasHardBlocker(review)) {
            return false;
        }
        return review.humanNaturalnessScore() >= valueOrDefault(request.minimumHumanNaturalnessScore(), 82)
                && review.factualGroundingScore() >= valueOrDefault(request.minimumFactualGroundingScore(), 85)
                && review.readabilityScore() >= valueOrDefault(request.minimumReadabilityScore(), 80)
                && review.seoReadinessScore() >= valueOrDefault(request.minimumSeoReadinessScore(), 70)
                && review.monetizationReadinessScore() >= valueOrDefault(request.minimumMonetizationReadinessScore(), 55);
    }

    private boolean hasHardBlocker(BlogPostQualityReviewResponse review) {
        if (!review.unsupportedClaims().isEmpty()) {
            return true;
        }

        String joinedReviewText = String.join(" ", review.issues())
                + " " + String.join(" ", review.aiLikePhrases())
                + " " + String.join(" ", review.monetizationSuggestions())
                + " " + review.verdict();
        return joinedReviewText.contains("placeholder")
                || joinedReviewText.contains("임시")
                || joinedReviewText.contains("예시")
                || joinedReviewText.contains("실제 정보 제공 미흡")
                || joinedReviewText.contains("가짜")
                || joinedReviewText.contains("02-0000")
                || joinedReviewText.contains("`#`")
                || joinedReviewText.contains("링크가 '#'");
    }

    private String buildRevisionInstruction(
            BlogPostQualityReviewResponse review,
            BlogPostQualityImproveRequest request,
            int round
    ) {
        String baseInstruction = review.revisionInstruction() == null || review.revisionInstruction().isBlank()
                ? "AI처럼 보이는 일반론을 줄이고, 입력 근거가 없는 문장은 제거하거나 개인 경험 기준으로 완화하세요."
                : review.revisionInstruction().trim();

        return """
                품질 개선 %d회차입니다.
                아래 품질 리뷰 결과를 반영해서 글을 다시 다듬어 주세요.

                목표 기준:
                - 자연스러움 %d점 이상
                - 근거 충실도 %d점 이상
                - 가독성 %d점 이상
                - SEO 준비도 %d점 이상
                - 수익화 준비도 %d점 이상

                현재 리뷰 요약:
                %s

                AI 티가 나는 표현:
                %s

                근거 없이 보이는 문장:
                %s

                 수익화 보완 제안:
                 %s

                 레퍼런스 피드백:
                 %s

                 레퍼런스 문장/구조 참고:
                 %s

                 네이버 블로그 피드백:
                 %s

                 네이버 블로그 제목 후보:
                 %s

                 네이버 블로그 구조 보완:
                 %s

                 네이버 상위 글 비교 피드백:
                 %s

                 네이버 상위 글 제목 패턴:
                 %s

                 네이버 상위 글 구조 패턴:
                 %s

                 수정 지시:
                 %s

                주의:
                - 사용자가 주지 않은 사실, 가격, 메뉴, 기능, 장애 상황은 새로 만들지 마세요.
                - 실제 URL이 제공되지 않은 예약 링크, 지도 링크, 제휴 링크를 만들지 마세요. `#`, `링크`, `example.com` 같은 임시 링크도 넣지 마세요.
                 - 실제 전화번호가 제공되지 않았다면 `02-0000-0000` 같은 예시 번호를 만들지 마세요.
                 - 수익화 제안은 실제 정보가 없으면 본문에 가짜 링크로 넣지 말고, "방문 전 공식 채널에서 확인"처럼 안전하게 표현하세요.
                 - 부족한 정보는 단정하지 말고 경험 기반 표현이나 확인 필요 표현으로 완화하세요.
                 - 광고 문구처럼 과장하지 말고 사람이 쓴 후기나 개발 기록처럼 구체적으로 정리하세요.
                 - 일반 블로그는 네이버 블로그에 붙여넣기 좋게 짧은 문단, 자연스러운 키워드, 구체적인 사진 설명, 과하지 않은 제목으로 다듬으세요.
                 - 네이버 검색 노출만을 위한 키워드 반복이나 무관한 인기 키워드는 넣지 마세요.
                 - 네이버 상위 글 패턴은 제목/구조 전략으로만 참고하고, 원문 문장이나 타인의 경험은 복사하지 마세요.
                 """.formatted(
                round,
                valueOrDefault(request.minimumHumanNaturalnessScore(), 82),
                valueOrDefault(request.minimumFactualGroundingScore(), 85),
                valueOrDefault(request.minimumReadabilityScore(), 80),
                valueOrDefault(request.minimumSeoReadinessScore(), 70),
                valueOrDefault(request.minimumMonetizationReadinessScore(), 55),
                review.verdict(),
                review.aiLikePhrases(),
                review.unsupportedClaims(),
                review.monetizationSuggestions(),
                review.referenceFeedback(),
                review.referenceSentenceSuggestions(),
                review.naverBlogFeedback(),
                review.naverBlogTitleSuggestions(),
                review.naverBlogStructureSuggestions(),
                review.naverTrendFeedback(),
                review.naverTrendTitlePatterns(),
                review.naverTrendStructurePatterns(),
                baseInstruction
        );
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
