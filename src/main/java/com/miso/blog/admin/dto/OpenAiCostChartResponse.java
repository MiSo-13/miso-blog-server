package com.miso.blog.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OpenAiCostChartResponse(
        LocalDateTime startAt,
        LocalDateTime endAt,
        String bucketWidth,
        BigDecimal totalCostUsd,
        List<Bucket> buckets
) {
    public record Bucket(
            LocalDateTime startAt,
            LocalDateTime endAt,
            BigDecimal costUsd,
            List<Result> results
    ) {
    }

    public record Result(
            String projectId,
            String lineItem,
            BigDecimal amountUsd,
            String currency
    ) {
    }
}
