package com.miso.blog.publish.dto;

import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.entity.PublishTargetEntity;

import java.time.LocalDateTime;

public record PublishTargetResponse(
        Long id,
        PublishChannel channel,
        PublishRole role,
        String name,
        String baseUrl,
        String repositoryFullName,
        String branchName,
        String contentRootPath,
        String customDomain,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PublishTargetResponse from(PublishTargetEntity target) {
        return new PublishTargetResponse(
                target.getId(),
                target.getChannel(),
                target.getRole(),
                target.getName(),
                target.getBaseUrl(),
                target.getRepositoryFullName(),
                target.getBranchName(),
                target.getContentRootPath(),
                target.getCustomDomain(),
                target.isActive(),
                target.getCreatedAt(),
                target.getUpdatedAt()
        );
    }
}
