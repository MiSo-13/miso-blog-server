package com.miso.blog.git.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RepositoryCommitSnapshot(
        String sha,
        String message,
        String authorName,
        LocalDateTime committedAt,
        List<RepositoryFilePatch> files
) {
}
