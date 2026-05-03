package com.miso.blog.ai.job.dto;

import com.miso.blog.ai.job.code.AiJobFailureCode;

import java.time.LocalDateTime;

public record AiJobFailureResponse(
        AiJobFailureCode code,
        String message,
        String detailMessage,
        boolean retryable,
        String actionGuide,
        LocalDateTime failedAt
) {
}
