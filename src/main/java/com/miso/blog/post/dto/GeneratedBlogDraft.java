package com.miso.blog.post.dto;

import java.util.List;

public record GeneratedBlogDraft(
        String title,
        String summary,
        String contentMarkdown,
        List<String> tags,
        String rawResponse,
        String modelName
) {
}
