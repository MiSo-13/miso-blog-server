package com.miso.blog.localrepo.dto;

public record LocalRepositoryDefaultResponse(
        String name,
        String localPath,
        String normalizedLocalPath,
        String defaultBranch,
        String description,
        boolean active,
        boolean readable,
        boolean registered,
        String message
) {
}
