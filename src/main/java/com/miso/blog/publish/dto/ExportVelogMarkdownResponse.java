package com.miso.blog.publish.dto;

import java.util.List;

public record ExportVelogMarkdownResponse(
        Long blogPostId,
        Long targetId,
        String targetName,
        String title,
        String summary,
        List<String> tags,
        String markdown,
        String canonicalUrl,
        String guide
) {
}
