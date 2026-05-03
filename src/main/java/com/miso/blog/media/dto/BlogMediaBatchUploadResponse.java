package com.miso.blog.media.dto;

import java.util.List;

public record BlogMediaBatchUploadResponse(
        String uploadGroupId,
        int uploadedCount,
        List<BlogMediaAssetResponse> assets
) {
}
