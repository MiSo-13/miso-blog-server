package com.miso.blog.localrepo.dto;

import com.miso.blog.localrepo.code.LocalRepositoryAnalysisMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AnalyzeLocalRepositoryRequest(
        @Min(1)
        @Max(50)
        Integer commitLimit,

        Boolean includeUncommittedChanges,

        LocalRepositoryAnalysisMode analysisMode,

        @Size(max = 2000)
        String focus,

        Boolean createBlogPost
) {
}
