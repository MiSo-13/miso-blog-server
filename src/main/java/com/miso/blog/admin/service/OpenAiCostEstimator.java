package com.miso.blog.admin.service;

import com.miso.blog.admin.dto.OpenAiEstimateResponse;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

@Component
public class OpenAiCostEstimator {
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private static final Map<String, ModelPrice> MODEL_PRICES = Map.ofEntries(
            Map.entry("gpt-4.1", new ModelPrice("2.00", "0.50", "8.00")),
            Map.entry("gpt-4.1-mini", new ModelPrice("0.40", "0.10", "1.60")),
            Map.entry("gpt-4.1-nano", new ModelPrice("0.10", "0.025", "0.40")),
            Map.entry("gpt-4o", new ModelPrice("2.50", "1.25", "10.00")),
            Map.entry("gpt-4o-mini", new ModelPrice("0.15", "0.075", "0.60")),
            Map.entry("gpt-5", new ModelPrice("1.25", "0.125", "10.00")),
            Map.entry("gpt-5-mini", new ModelPrice("0.25", "0.025", "2.00")),
            Map.entry("gpt-5-nano", new ModelPrice("0.05", "0.005", "0.40"))
    );

    @Value("${blog.ai.model:gpt-4.1-mini}")
    private String defaultModel;

    public OpenAiEstimateResponse estimate(String requestedModel, Long inputTokens, Long cachedInputTokens, Long outputTokens) {
        String model = normalizeModel(requestedModel);
        ModelPrice price = MODEL_PRICES.get(model);
        if (price == null) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "예상 비용 계산을 지원하지 않는 모델입니다. model=" + model);
        }

        long normalizedInputTokens = normalizeCount(inputTokens);
        long normalizedCachedInputTokens = Math.min(normalizeCount(cachedInputTokens), normalizedInputTokens);
        long billableInputTokens = normalizedInputTokens - normalizedCachedInputTokens;
        long normalizedOutputTokens = normalizeCount(outputTokens);

        BigDecimal inputCost = price.inputPerMillionUsd()
                .multiply(BigDecimal.valueOf(billableInputTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        BigDecimal cachedInputCost = price.cachedInputPerMillionUsd()
                .multiply(BigDecimal.valueOf(normalizedCachedInputTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = price.outputPerMillionUsd()
                .multiply(BigDecimal.valueOf(normalizedOutputTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);

        return new OpenAiEstimateResponse(
                model,
                normalizedInputTokens,
                normalizedCachedInputTokens,
                billableInputTokens,
                normalizedOutputTokens,
                price.inputPerMillionUsd(),
                price.cachedInputPerMillionUsd(),
                price.outputPerMillionUsd(),
                inputCost.add(cachedInputCost).add(outputCost).setScale(8, RoundingMode.HALF_UP),
                "공식 가격표 기반의 사전 추정값입니다. 실제 청구 금액은 OpenAI Costs API 기준으로 확인하세요."
        );
    }

    private String normalizeModel(String requestedModel) {
        String model = requestedModel == null || requestedModel.isBlank() ? defaultModel : requestedModel;
        return model.trim().toLowerCase(Locale.ROOT);
    }

    private long normalizeCount(Long value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private record ModelPrice(
            BigDecimal inputPerMillionUsd,
            BigDecimal cachedInputPerMillionUsd,
            BigDecimal outputPerMillionUsd
    ) {
        private ModelPrice(String inputPerMillionUsd, String cachedInputPerMillionUsd, String outputPerMillionUsd) {
            this(new BigDecimal(inputPerMillionUsd), new BigDecimal(cachedInputPerMillionUsd), new BigDecimal(outputPerMillionUsd));
        }
    }
}
