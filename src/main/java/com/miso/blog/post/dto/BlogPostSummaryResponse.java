package com.miso.blog.post.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.entity.BlogPostEntity;

import java.time.LocalDateTime;
import java.util.List;

public record BlogPostSummaryResponse(
        Long id,
        String title,
        String slug,
        String summary,
        List<String> tags,
        BlogPostStatus status,
        int currentVersionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BlogPostSummaryResponse from(BlogPostEntity blogPost, ObjectMapper objectMapper) {
        BlogPostResponse detail = BlogPostResponse.from(blogPost, objectMapper);
        return new BlogPostSummaryResponse(
                detail.id(),
                detail.title(),
                detail.slug(),
                detail.summary(),
                detail.tags(),
                detail.status(),
                detail.currentVersionNo(),
                detail.createdAt(),
                detail.updatedAt()
        );
    }
}
