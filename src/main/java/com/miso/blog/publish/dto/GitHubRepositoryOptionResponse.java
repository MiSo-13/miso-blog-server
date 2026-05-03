package com.miso.blog.publish.dto;

public record GitHubRepositoryOptionResponse(
        String name,
        String fullName,
        String ownerLogin,
        String defaultBranch,
        boolean privateRepository,
        boolean fork,
        boolean githubPagesCandidate,
        String htmlUrl,
        String updatedAt
) {
}
