package com.miso.blog.post.dto;

import jakarta.validation.constraints.Size;

public record BlogPostQualityReviewRequest(
        @Size(max = 4000)
        String originalInputMemo,

        @Size(max = 1000)
        String targetReader,

        @Size(max = 1000)
        String monetizationGoal
) {
}
