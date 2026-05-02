package com.miso.blog.admin.dto;

import jakarta.validation.constraints.Min;

public class OpenAiEstimateRequest {
    private String model;

    @Min(0)
    private Long inputTokens;

    @Min(0)
    private Long cachedInputTokens;

    @Min(0)
    private Long outputTokens;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Long getCachedInputTokens() {
        return cachedInputTokens;
    }

    public void setCachedInputTokens(Long cachedInputTokens) {
        this.cachedInputTokens = cachedInputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Long outputTokens) {
        this.outputTokens = outputTokens;
    }
}
