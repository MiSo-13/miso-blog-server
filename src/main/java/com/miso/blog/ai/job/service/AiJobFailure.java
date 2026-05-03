package com.miso.blog.ai.job.service;

import com.miso.blog.ai.job.code.AiJobFailureCode;

public record AiJobFailure(
        AiJobFailureCode code,
        String message,
        String detailMessage,
        boolean retryable,
        String actionGuide
) {
}
