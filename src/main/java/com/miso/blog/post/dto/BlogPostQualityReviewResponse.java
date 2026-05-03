package com.miso.blog.post.dto;

import java.util.List;

public record BlogPostQualityReviewResponse(
        Long blogPostId,
        String verdict,
        int humanNaturalnessScore,
        int factualGroundingScore,
        int readabilityScore,
        int seoReadinessScore,
        int monetizationReadinessScore,
        boolean publishReady,
        List<String> strengths,
        List<String> issues,
        List<String> unsupportedClaims,
        List<String> aiLikePhrases,
        List<String> monetizationSuggestions,
        String revisionInstruction,
        String rawResponse,
        String modelName
) {
}
