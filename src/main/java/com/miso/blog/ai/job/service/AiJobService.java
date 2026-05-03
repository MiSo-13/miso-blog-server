package com.miso.blog.ai.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.ai.job.code.AiJobStatus;
import com.miso.blog.ai.job.code.AiJobType;
import com.miso.blog.ai.job.dto.AiJobResponse;
import com.miso.blog.ai.job.entity.AiJobEntity;
import com.miso.blog.ai.job.repository.AiJobRepository;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.dto.BlogPostQualityImproveRequest;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.dto.ReviseBlogPostWithAiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiJobService {
    private static final int MAX_RETRY_COUNT = 3;

    private final AiJobRepository aiJobRepository;
    private final AiJobStateService aiJobStateService;
    private final AiJobWorker aiJobWorker;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiJobResponse createGeneralBlogDraftJob(CreateGeneralBlogPostRequest request) {
        AiJobEntity job = savePendingJob(AiJobType.GENERAL_BLOG_DRAFT, request, 0, null);
        afterCommit(() -> aiJobWorker.runGeneralBlogDraftJob(job.getId(), request));
        return AiJobResponse.from(job);
    }

    @Transactional
    public AiJobResponse createBlogPostRevisionJob(Long blogPostId, ReviseBlogPostWithAiRequest request) {
        BlogPostRevisionJobRequest jobRequest = new BlogPostRevisionJobRequest(blogPostId, request);
        AiJobEntity job = savePendingJob(AiJobType.BLOG_POST_REVISION, jobRequest, 0, null);
        afterCommit(() -> aiJobWorker.runBlogPostRevisionJob(job.getId(), blogPostId, request));
        return AiJobResponse.from(job);
    }

    @Transactional
    public AiJobResponse createBlogPostQualityImproveJob(Long blogPostId, BlogPostQualityImproveRequest request) {
        BlogPostQualityImproveJobRequest jobRequest = new BlogPostQualityImproveJobRequest(blogPostId, request);
        AiJobEntity job = savePendingJob(AiJobType.BLOG_POST_QUALITY_IMPROVE, jobRequest, 0, null);
        afterCommit(() -> aiJobWorker.runBlogPostQualityImproveJob(job.getId(), blogPostId, request));
        return AiJobResponse.from(job);
    }

    @Transactional
    public AiJobResponse retryJob(Long failedJobId) {
        AiJobEntity failedJob = aiJobRepository.findById(failedJobId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "AI 작업을 찾을 수 없습니다."));
        validateRetryable(failedJob);

        int nextRetryCount = failedJob.getRetryCount() + 1;
        AiJobEntity retryJob = aiJobRepository.save(AiJobEntity.builder()
                .type(failedJob.getType())
                .status(AiJobStatus.PENDING)
                .requestJson(failedJob.getRequestJson())
                .retryCount(nextRetryCount)
                .retriedFromJobId(failedJob.getId())
                .build());

        afterCommit(() -> runRetryJob(retryJob.getId(), failedJob.getType(), failedJob.getRequestJson()));
        return AiJobResponse.from(retryJob);
    }

    @Transactional(readOnly = true)
    public List<AiJobResponse> getJobs() {
        return aiJobRepository.findAllByOrderByIdDesc()
                .stream()
                .map(AiJobResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiJobResponse getJob(Long jobId) {
        return aiJobStateService.getJob(jobId);
    }

    private AiJobEntity savePendingJob(AiJobType type, Object request, int retryCount, Long retriedFromJobId) {
        return aiJobRepository.save(AiJobEntity.builder()
                .type(type)
                .status(AiJobStatus.PENDING)
                .requestJson(writeJson(request))
                .retryCount(retryCount)
                .retriedFromJobId(retriedFromJobId)
                .build());
    }

    private void validateRetryable(AiJobEntity failedJob) {
        if (failedJob.getStatus() != AiJobStatus.FAILED) {
            throw new GeneralException(ErrorCode.CONFLICT, "실패한 AI 작업만 재시도할 수 있습니다.");
        }
        if (!Boolean.TRUE.equals(failedJob.getFailureRetryable())) {
            throw new GeneralException(ErrorCode.CONFLICT, "재시도해도 해결되기 어려운 실패입니다. 설정이나 입력값을 먼저 수정하세요.");
        }
        if (failedJob.getRetryCount() >= MAX_RETRY_COUNT) {
            throw new GeneralException(ErrorCode.CONFLICT, "AI 작업 재시도 횟수를 초과했습니다.");
        }
    }

    private void runRetryJob(Long retryJobId, AiJobType type, String requestJson) {
        try {
            switch (type) {
                case GENERAL_BLOG_DRAFT -> {
                    CreateGeneralBlogPostRequest request = readJson(requestJson, CreateGeneralBlogPostRequest.class);
                    aiJobWorker.runGeneralBlogDraftJob(retryJobId, request);
                }
                case BLOG_POST_REVISION -> {
                    BlogPostRevisionJobRequest jobRequest = readJson(requestJson, BlogPostRevisionJobRequest.class);
                    aiJobWorker.runBlogPostRevisionJob(retryJobId, jobRequest.blogPostId(), jobRequest.request());
                }
                case BLOG_POST_QUALITY_IMPROVE -> {
                    BlogPostQualityImproveJobRequest jobRequest = readJson(requestJson, BlogPostQualityImproveJobRequest.class);
                    aiJobWorker.runBlogPostQualityImproveJob(retryJobId, jobRequest.blogPostId(), jobRequest.request());
                }
            }
        } catch (Exception exception) {
            aiJobStateService.markFailed(retryJobId, exception);
        }
    }

    private void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "AI 작업 요청을 저장할 수 없습니다.");
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "AI 작업 요청을 다시 읽을 수 없습니다.");
        }
    }

    private record BlogPostRevisionJobRequest(
            Long blogPostId,
            ReviseBlogPostWithAiRequest request
    ) {
    }

    private record BlogPostQualityImproveJobRequest(
            Long blogPostId,
            BlogPostQualityImproveRequest request
    ) {
    }
}
