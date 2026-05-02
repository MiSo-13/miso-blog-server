package com.miso.blog.publish.dto;

public record PublishGithubPagesResponse(
        Long blogPostId,
        String status,
        Long targetId,
        String repositoryFullName,
        String branchName,
        String filePath,
        String commitSha,
        String commitUrl,
        String contentUrl,
        String expectedPublicUrl
) {
}
