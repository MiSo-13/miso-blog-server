package com.miso.blog.reference.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.entity.BlogReferenceUrlEntity;

import java.time.LocalDateTime;
import java.util.List;

public record BlogReferenceUrlResponse(
        Long id,
        BlogReferenceType type,
        String title,
        String url,
        String description,
        List<String> tags,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BlogReferenceUrlResponse from(BlogReferenceUrlEntity referenceUrl, ObjectMapper objectMapper) {
        return new BlogReferenceUrlResponse(
                referenceUrl.getId(),
                referenceUrl.getType(),
                referenceUrl.getTitle(),
                referenceUrl.getUrl(),
                referenceUrl.getDescription(),
                readTags(referenceUrl.getTagsJson(), objectMapper),
                referenceUrl.isActive(),
                referenceUrl.getCreatedAt(),
                referenceUrl.getUpdatedAt()
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
