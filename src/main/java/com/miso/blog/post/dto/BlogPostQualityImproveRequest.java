package com.miso.blog.post.dto;

import com.miso.blog.post.code.GeneralBlogLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record BlogPostQualityImproveRequest(
        @Valid
        BlogPostQualityReviewRequest reviewRequest,

        @Min(1)
        @Max(3)
        Integer maxRevisionRounds,

        @Min(0)
        @Max(100)
        Integer minimumHumanNaturalnessScore,

        @Min(0)
        @Max(100)
        Integer minimumFactualGroundingScore,

        @Min(0)
        @Max(100)
        Integer minimumReadabilityScore,

        @Min(0)
        @Max(100)
        Integer minimumSeoReadinessScore,

        @Min(0)
        @Max(100)
        Integer minimumMonetizationReadinessScore,

        @Size(max = 4000)
        String additionalRevisionMemo,

        @Size(max = 200)
        String tone,

        GeneralBlogLength targetLength,

        Boolean preserveTitle,

        Boolean preserveTags,

        Boolean requirePublishReady,

        Boolean markReviewReadyWhenPassed
) {
}
