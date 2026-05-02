package com.miso.blog.git.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AnalyzeGitRepositoryRequest(
        @Min(1)
        @Max(30)
        Integer commitLimit,

        @Size(max = 2000)
        String focus,

        Boolean createBlogPost
) {
}
