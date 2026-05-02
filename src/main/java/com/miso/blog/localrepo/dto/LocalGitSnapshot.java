package com.miso.blog.localrepo.dto;

public record LocalGitSnapshot(
        String branchName,
        String sourceSummary
) {
}
