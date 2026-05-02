package com.miso.blog.publish.dto;

public record GitHubContentCommitResult(
        String filePath,
        String commitSha,
        String commitUrl,
        String contentUrl
) {
}
