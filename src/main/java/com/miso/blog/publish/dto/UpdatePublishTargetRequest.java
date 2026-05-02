package com.miso.blog.publish.dto;

import com.miso.blog.publish.code.PublishRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePublishTargetRequest(
        @NotNull
        PublishRole role,

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String baseUrl,

        @Size(max = 200)
        String repositoryFullName,

        @Size(max = 100)
        String branchName,

        @Size(max = 300)
        String contentRootPath,

        @Size(max = 200)
        String customDomain,

        Boolean active
) {
}
