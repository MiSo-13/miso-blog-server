package com.miso.blog.localrepo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLocalRepositoryRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Size(max = 1000)
        String localPath,

        @Size(max = 100)
        String defaultBranch,

        @Size(max = 500)
        String description,

        Boolean active
) {
}
