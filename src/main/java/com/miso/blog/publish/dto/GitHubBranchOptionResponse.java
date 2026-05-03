package com.miso.blog.publish.dto;

public record GitHubBranchOptionResponse(
        String name,
        String commitSha,
        boolean protectedBranch
) {
}
