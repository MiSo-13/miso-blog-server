package com.miso.blog.post.dto;

import com.miso.blog.post.code.BlogPostVersionAction;
import com.miso.blog.post.entity.BlogPostVersionEntity;

import java.time.LocalDateTime;

public record BlogPostVersionResponse(
        Long id,
        int versionNo,
        BlogPostVersionAction action,
        String title,
        String slug,
        String summary,
        String contentMarkdown,
        String tagsJson,
        LocalDateTime createdAt
) {
    public static BlogPostVersionResponse from(BlogPostVersionEntity version) {
        return new BlogPostVersionResponse(
                version.getId(),
                version.getVersionNo(),
                version.getAction(),
                version.getTitle(),
                version.getSlug(),
                version.getSummary(),
                version.getContentMarkdown(),
                version.getTagsJson(),
                version.getCreatedAt()
        );
    }
}
