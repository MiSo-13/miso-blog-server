package com.miso.blog.post.dto;

import com.miso.blog.post.code.BlogWritingMode;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBlogPostFromAnalysisRequest(
        List<String> selectedKeywords,

        @Size(max = 300)
        String selectedTopicTitle,

        @Size(max = 2000)
        String writingFocus,

        @Size(max = 1000)
        String audience,

        BlogWritingMode writingMode,

        Boolean markReviewReady
) {
}
