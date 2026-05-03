package com.miso.blog.git.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AnalyzeGitRepositoryRequest(
        @Min(1)
        @Max(300)
        Integer commitLimit,

        Boolean analyzeAllCommits,

        @Size(max = 2000)
        String focus,

        Boolean createBlogPost
) {
}
