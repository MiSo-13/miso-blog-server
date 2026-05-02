package com.miso.blog.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBlogPostRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 220)
        String slug,

        @Size(max = 1000)
        String summary,

        @NotBlank
        String contentMarkdown,

        List<String> tags,

        String sourceNote
) {
}
