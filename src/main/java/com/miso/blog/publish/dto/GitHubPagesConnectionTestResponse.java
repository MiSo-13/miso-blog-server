package com.miso.blog.publish.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GitHubPagesConnectionTestResponse(
        Long targetId,
        String repositoryFullName,
        String branchName,
        String contentRootPath,
        boolean success,
        List<String> checkedItems,
        List<String> warnings,
        String repositoryUrl,
        String branchUrl,
        String contentRootUrl,
        String message,
        LocalDateTime checkedAt
) {
}
