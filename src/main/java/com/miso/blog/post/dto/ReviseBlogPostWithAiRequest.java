package com.miso.blog.post.dto;

import com.miso.blog.post.code.GeneralBlogLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviseBlogPostWithAiRequest(
        @NotBlank
        @Size(max = 4000)
        String revisionInstruction,

        @Size(max = 4000)
        String additionalMemo,

        @Size(max = 200)
        String tone,

        GeneralBlogLength targetLength,

        Boolean preserveTitle,

        Boolean preserveTags,

        Boolean markReviewReady
) {
}
