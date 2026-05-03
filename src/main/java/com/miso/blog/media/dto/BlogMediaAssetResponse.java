package com.miso.blog.media.dto;

import com.miso.blog.media.entity.BlogMediaAssetEntity;

import java.time.LocalDateTime;

public record BlogMediaAssetResponse(
        Long id,
        String originalFilename,
        String storedFilename,
        String contentType,
        long fileSize,
        String relativePath,
        String publicUrl,
        String uploadGroupId,
        String altText,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BlogMediaAssetResponse from(BlogMediaAssetEntity asset) {
        return new BlogMediaAssetResponse(
                asset.getId(),
                asset.getOriginalFilename(),
                asset.getStoredFilename(),
                asset.getContentType(),
                asset.getFileSize(),
                asset.getRelativePath(),
                asset.getPublicUrl(),
                asset.getUploadGroupId(),
                asset.getAltText(),
                asset.getNote(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
