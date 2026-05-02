package com.miso.blog.git.dto;

import com.miso.blog.git.entity.GitRepositoryEntity;

import java.time.LocalDateTime;

public record GitRepositoryResponse(
        Long id,
        String repositoryFullName,
        String defaultBranch,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GitRepositoryResponse from(GitRepositoryEntity repository) {
        return new GitRepositoryResponse(
                repository.getId(),
                repository.getRepositoryFullName(),
                repository.getDefaultBranch(),
                repository.getDescription(),
                repository.isActive(),
                repository.getCreatedAt(),
                repository.getUpdatedAt()
        );
    }
}
