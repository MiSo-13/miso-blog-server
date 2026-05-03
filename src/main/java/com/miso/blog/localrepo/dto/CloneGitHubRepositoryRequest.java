package com.miso.blog.localrepo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloneGitHubRepositoryRequest(
        @NotBlank
        @Size(max = 200)
        String repositoryFullName,

        @Size(max = 100)
        String branchName,

        @Size(max = 120)
        String name,

        @Size(max = 500)
        String description,

        Boolean refreshExisting
) {
}
