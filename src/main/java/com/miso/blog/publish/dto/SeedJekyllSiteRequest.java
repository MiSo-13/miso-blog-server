package com.miso.blog.publish.dto;

import jakarta.validation.constraints.Size;

public record SeedJekyllSiteRequest(
        @Size(max = 120)
        String siteTitle,

        @Size(max = 300)
        String siteDescription,

        @Size(max = 100)
        String authorName,

        @Size(max = 500)
        String baseUrl,

        Boolean forceOverwrite,

        @Size(max = 200)
        String commitMessage
) {
}
