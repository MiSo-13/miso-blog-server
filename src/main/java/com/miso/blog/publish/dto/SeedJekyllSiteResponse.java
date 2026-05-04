package com.miso.blog.publish.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeedJekyllSiteResponse(
        Long targetId,
        String repositoryFullName,
        String branchName,
        String publicBaseUrl,
        boolean forceOverwrite,
        List<JekyllScaffoldFileResponse> files,
        String commitSha,
        String commitUrl,
        LocalDateTime seededAt
) {
}
