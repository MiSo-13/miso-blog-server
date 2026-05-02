package com.miso.blog.publish.dto;

public record ExportVelogMarkdownRequest(
        Long targetId,
        String canonicalUrl,
        Boolean includeCanonicalLink,
        Boolean includeSourceNote
) {
}
