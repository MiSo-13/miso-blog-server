package com.miso.blog.localrepo.dto;

import com.miso.blog.localrepo.entity.LocalRepositoryEntity;

import java.time.LocalDateTime;

public record LocalRepositoryResponse(
        Long id,
        String name,
        String localPath,
        String defaultBranch,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LocalRepositoryResponse from(LocalRepositoryEntity repository) {
        return new LocalRepositoryResponse(
                repository.getId(),
                repository.getName(),
                repository.getLocalPath(),
                repository.getDefaultBranch(),
                repository.getDescription(),
                repository.isActive(),
                repository.getCreatedAt(),
                repository.getUpdatedAt()
        );
    }
}
