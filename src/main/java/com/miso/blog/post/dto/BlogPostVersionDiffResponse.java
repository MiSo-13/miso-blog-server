package com.miso.blog.post.dto;

import java.util.List;

public record BlogPostVersionDiffResponse(
        Long blogPostId,
        int fromVersionNo,
        int toVersionNo,
        int addedLineCount,
        int deletedLineCount,
        boolean changed,
        BlogPostVersionResponse fromVersion,
        BlogPostVersionResponse toVersion,
        List<BlogPostVersionDiffSectionResponse> sections
) {
}
