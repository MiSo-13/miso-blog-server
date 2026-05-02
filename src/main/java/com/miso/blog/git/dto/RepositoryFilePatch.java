package com.miso.blog.git.dto;

public record RepositoryFilePatch(
        String filename,
        String status,
        Integer additions,
        Integer deletions,
        String patch
) {
}
