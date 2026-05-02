package com.miso.blog.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ai_usage_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_job_id")
    private Long aiJobId;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "cached_input_tokens", nullable = false)
    private long cachedInputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "estimated_cost_usd", precision = 18, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "request_started_at", nullable = false)
    private LocalDateTime requestStartedAt;

    @Column(name = "request_finished_at", nullable = false)
    private LocalDateTime requestFinishedAt;

    @Builder
    public AiUsageLogEntity(
            Long aiJobId,
            String provider,
            String model,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            BigDecimal estimatedCostUsd,
            boolean success,
            String errorCode,
            LocalDateTime requestStartedAt,
            LocalDateTime requestFinishedAt
    ) {
        this.aiJobId = aiJobId;
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.cachedInputTokens = cachedInputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCostUsd = estimatedCostUsd;
        this.success = success;
        this.errorCode = errorCode;
        this.requestStartedAt = requestStartedAt;
        this.requestFinishedAt = requestFinishedAt;
    }
}
