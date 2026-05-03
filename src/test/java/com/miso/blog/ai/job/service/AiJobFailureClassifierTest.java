package com.miso.blog.ai.job.service;

import com.miso.blog.ai.job.code.AiJobFailureCode;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.common.security.SecretMaskingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiJobFailureClassifierTest {
    private final AiJobFailureClassifier classifier = new AiJobFailureClassifier(new SecretMaskingService());

    @Test
    void classifyRateLimitAsRetryable() {
        AiJobFailure failure = classifier.classify(new GeneralException(
                ErrorCode.BAD_REQUEST,
                "OpenAI 요청 호출에 실패했습니다. status=429"
        ));

        assertEquals(AiJobFailureCode.OPENAI_RATE_LIMIT, failure.code());
        assertTrue(failure.retryable());
    }

    @Test
    void classifyMissingApiKeyAsNotRetryable() {
        AiJobFailure failure = classifier.classify(new GeneralException(
                ErrorCode.BAD_REQUEST,
                "OpenAI API Key가 설정되어 있지 않습니다."
        ));

        assertEquals(AiJobFailureCode.OPENAI_API_KEY_MISSING, failure.code());
        assertFalse(failure.retryable());
    }

    @Test
    void classifyNotFoundAsNotRetryable() {
        AiJobFailure failure = classifier.classify(new GeneralException(
                ErrorCode.NOT_FOUND,
                "블로그 글을 찾을 수 없습니다."
        ));

        assertEquals(AiJobFailureCode.TARGET_NOT_FOUND, failure.code());
        assertFalse(failure.retryable());
    }
}
