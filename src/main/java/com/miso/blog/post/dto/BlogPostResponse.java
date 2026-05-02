package com.miso.blog.post.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.entity.BlogPostEntity;

import java.time.LocalDateTime;
import java.util.List;

public record BlogPostResponse(
        Long id,
        String title,
        String slug,
        String summary,
        String contentMarkdown,
        List<String> tags,
        String sourceNote,
        BlogPostStatus status,
        int currentVersionNo,
        LocalDateTime approvedAt,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BlogPostResponse from(BlogPostEntity blogPost, ObjectMapper objectMapper) {
        return new BlogPostResponse(
                blogPost.getId(),
                blogPost.getTitle(),
                blogPost.getSlug(),
                blogPost.getSummary(),
                blogPost.getContentMarkdown(),
                readTags(blogPost.getTagsJson(), objectMapper),
                blogPost.getSourceNote(),
                blogPost.getStatus(),
                blogPost.getCurrentVersionNo(),
                blogPost.getApprovedAt(),
                blogPost.getPublishedAt(),
                blogPost.getCreatedAt(),
                blogPost.getUpdatedAt()
        );
    }

    private static List<String> readTags(String tagsJson, ObjectMapper objectMapper) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
