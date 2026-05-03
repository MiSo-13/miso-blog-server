package com.miso.blog.post.dto;

import java.util.List;

public record BlogPostVersionDiffSectionResponse(
        String fieldName,
        boolean changed,
        int addedLineCount,
        int deletedLineCount,
        List<BlogPostVersionDiffLineResponse> lines
) {
}
