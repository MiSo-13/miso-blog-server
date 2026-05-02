package com.miso.blog.admin.dto;

import java.math.BigDecimal;

public record OpenAiSummaryResponse(
        boolean apiKeyConfigured,
        boolean adminKeyConfigured,
        String effectiveKeyType,
        String keyLabel,
        String model,
        boolean costApiAvailable,
        BigDecimal todayCostUsd,
        BigDecimal monthToDateCostUsd,
        BigDecimal budgetLimitUsd,
        BigDecimal remainingBudgetUsd,
        String unavailableReason,
        String usageDashboardUrl,
        String billingUrl
) {
}
