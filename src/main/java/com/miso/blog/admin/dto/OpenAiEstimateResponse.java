package com.miso.blog.admin.dto;

import java.math.BigDecimal;

public record OpenAiEstimateResponse(
        String model,
        long inputTokens,
        long cachedInputTokens,
        long billableInputTokens,
        long outputTokens,
        BigDecimal inputPricePerMillionUsd,
        BigDecimal cachedInputPricePerMillionUsd,
        BigDecimal outputPricePerMillionUsd,
        BigDecimal estimatedCostUsd,
        String pricingNote
) {
}
