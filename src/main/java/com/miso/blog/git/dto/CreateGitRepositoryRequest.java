package com.miso.blog.git.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateGitRepositoryRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", message = "owner/repo 형식이어야 합니다.")
        String repositoryFullName,

        @Size(max = 100)
        String defaultBranch,

        @Size(max = 500)
        String description,

        Boolean active
) {
}
