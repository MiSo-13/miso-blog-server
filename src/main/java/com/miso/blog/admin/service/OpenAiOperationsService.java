package com.miso.blog.admin.service;

import com.miso.blog.admin.dto.OpenAiCostChartResponse;
import com.miso.blog.admin.dto.OpenAiCostsRequest;
import com.miso.blog.admin.dto.OpenAiEstimateRequest;
import com.miso.blog.admin.dto.OpenAiEstimateResponse;
import com.miso.blog.admin.dto.OpenAiSummaryResponse;
import com.miso.blog.admin.dto.OpenAiUsageRequest;
import com.miso.blog.admin.dto.OpenAiUsageResponse;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenAiOperationsService {
    private static final String OPENAI_USAGE_DASHBOARD_URL = "https://platform.openai.com/usage";
    private static final String OPENAI_BILLING_URL = "https://platform.openai.com/settings/organization/billing/overview";

    private final OpenAiAdminClient openAiAdminClient;
    private final OpenAiCostEstimator openAiCostEstimator;

    @Value("${blog.ai.model:gpt-4.1-mini}")
    private String model;

    @Value("${blog.ai.budget-limit-usd:}")
    private String budgetLimitUsd;

    public OpenAiSummaryResponse getSummary() {
        OpenAiAdminClient.OpenAiCostSummary costSummary = openAiAdminClient.fetchCurrentMonthCostSummary();
        BigDecimal budgetLimit = parseBudgetLimit();
        BigDecimal monthToDateCost = scale(costSummary.monthToDateCostUsd());
        BigDecimal remainingBudget = null;
        if (budgetLimit != null && monthToDateCost != null) {
            remainingBudget = budgetLimit.subtract(monthToDateCost).setScale(2, RoundingMode.HALF_UP);
        }

        return new OpenAiSummaryResponse(
                openAiAdminClient.isApiKeyConfigured(),
                openAiAdminClient.isAdminKeyConfigured(),
                openAiAdminClient.getEffectiveKeyType(),
                openAiAdminClient.getKeyLabel(),
                model,
                costSummary.available(),
                scale(costSummary.todayCostUsd()),
                monthToDateCost,
                budgetLimit,
                remainingBudget,
                costSummary.unavailableReason(),
                OPENAI_USAGE_DASHBOARD_URL,
                OPENAI_BILLING_URL
        );
    }

    public OpenAiCostChartResponse getCosts(OpenAiCostsRequest request) {
        try {
            return openAiAdminClient.fetchCostChart(
                    request.getStartDate(),
                    request.getEndDate(),
                    splitCsv(request.getGroupBy())
            );
        } catch (OpenAiAdminClient.OpenAiAdminClientException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, exception.getMessage());
        }
    }

    public OpenAiUsageResponse getCompletionUsage(OpenAiUsageRequest request) {
        try {
            return openAiAdminClient.fetchCompletionUsage(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getBucketWidth(),
                    splitCsv(request.getGroupBy())
            );
        } catch (OpenAiAdminClient.OpenAiAdminClientException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, exception.getMessage());
        }
    }

    public OpenAiEstimateResponse estimate(OpenAiEstimateRequest request) {
        return openAiCostEstimator.estimate(
                request.getModel(),
                request.getInputTokens(),
                request.getCachedInputTokens(),
                request.getOutputTokens()
        );
    }

    private List<String> splitCsv(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        return List.of(rawValue.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private BigDecimal parseBudgetLimit() {
        if (budgetLimitUsd == null || budgetLimitUsd.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(budgetLimitUsd).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
