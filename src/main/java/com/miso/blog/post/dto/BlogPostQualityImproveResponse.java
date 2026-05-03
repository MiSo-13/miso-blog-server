package com.miso.blog.post.dto;

import java.util.List;

public record BlogPostQualityImproveResponse(
        Long blogPostId,
        int revisionCount,
        boolean criteriaPassed,
        boolean publishReady,
        BlogPostQualityReviewResponse initialReview,
        BlogPostQualityReviewResponse finalReview,
        BlogPostResponse blogPost,
        List<String> revisionInstructions,
        String message
) {
}
