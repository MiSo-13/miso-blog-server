package com.miso.blog.publish.dto;

public record JekyllScaffoldFileResponse(
        String filePath,
        String action,
        String contentUrl
) {
}
