package com.miso.blog.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OpenAiUsageResponse(
        LocalDateTime startAt,
        LocalDateTime endAt,
        String bucketWidth,
        List<String> groupBy,
        long totalInputTokens,
        long totalCachedInputTokens,
        long totalOutputTokens,
        long totalRequests,
        List<Bucket> buckets
) {
    public record Bucket(
            LocalDateTime startAt,
            LocalDateTime endAt,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long requests,
            List<Result> results
    ) {
    }

    public record Result(
            String model,
            String apiKeyId,
            String projectId,
            String userId,
            Boolean batch,
            String serviceTier,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long requests
    ) {
    }
}
