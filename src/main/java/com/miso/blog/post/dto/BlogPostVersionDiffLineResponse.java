package com.miso.blog.post.dto;

public record BlogPostVersionDiffLineResponse(
        String type,
        Integer oldLineNo,
        Integer newLineNo,
        String text
) {
}
