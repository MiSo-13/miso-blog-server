package com.miso.blog.ai.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.ai.job.dto.AiJobResponse;
import com.miso.blog.ai.job.entity.AiJobEntity;
import com.miso.blog.ai.job.repository.AiJobRepository;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiJobStateService {
    private final AiJobRepository aiJobRepository;
    private final ObjectMapper objectMapper;
    private final AiJobFailureClassifier aiJobFailureClassifier;

    @Transactional
    public void markRunning(Long jobId) {
        AiJobEntity job = getJobOrThrow(jobId);
        job.markRunning();
    }

    @Transactional
    public void markSucceeded(Long jobId, Object result, Long resultBlogPostId) {
        AiJobEntity job = getJobOrThrow(jobId);
        job.markSucceeded(writeJson(result), resultBlogPostId);
    }

    @Transactional
    public void markFailed(Long jobId, Exception exception) {
        AiJobEntity job = getJobOrThrow(jobId);
        AiJobFailure failure = aiJobFailureClassifier.classify(exception);
        job.markFailed(new AiJobFailure(
                failure.code(),
                truncate(failure.message(), 1000),
                truncate(failure.detailMessage(), 2000),
                failure.retryable(),
                truncate(failure.actionGuide(), 1000)
        ));
    }

    @Transactional(readOnly = true)
    public AiJobResponse getJob(Long jobId) {
        return AiJobResponse.from(getJobOrThrow(jobId));
    }

    private AiJobEntity getJobOrThrow(Long jobId) {
        return aiJobRepository.findById(jobId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "AI 작업을 찾을 수 없습니다."));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "AI 작업 결과를 저장할 수 없습니다.");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
