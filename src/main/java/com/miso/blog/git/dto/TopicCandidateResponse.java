package com.miso.blog.git.dto;

import java.util.List;

public record TopicCandidateResponse(
        String title,
        String angle,
        String reason,
        List<String> sourceFiles,
        List<String> tags
) {
}
