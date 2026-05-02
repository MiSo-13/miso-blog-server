package com.miso.blog.publish.dto;

public record PublishGithubPagesRequest(
        Long targetId,
        String commitMessage
) {
}
