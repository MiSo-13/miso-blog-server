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
    private final AiJobRepository aiJobRepository;
    private final AiJobStateService aiJobStateService;
    private final AiJobWorker aiJobWorker;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiJobResponse createGeneralBlogDraftJob(CreateGeneralBlogPostRequest request) {
        AiJobEntity job = aiJobRepository.save(AiJobEntity.builder()
                .type(AiJobType.GENERAL_BLOG_DRAFT)
                .status(AiJobStatus.PENDING)
                .requestJson(writeJson(request))
                .build());
        afterCommit(() -> aiJobWorker.runGeneralBlogDraftJob(job.getId(), request));
        return AiJobResponse.from(job);
    }

    @Transactional
    public AiJobResponse createBlogPostRevisionJob(Long blogPostId, ReviseBlogPostWithAiRequest request) {
        AiJobEntity job = aiJobRepository.save(AiJobEntity.builder()
                .type(AiJobType.BLOG_POST_REVISION)
                .status(AiJobStatus.PENDING)
                .requestJson(writeJson(new BlogPostRevisionJobRequest(blogPostId, request)))
                .build());
        afterCommit(() -> aiJobWorker.runBlogPostRevisionJob(job.getId(), blogPostId, request));
        return AiJobResponse.from(job);
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

    private record BlogPostRevisionJobRequest(
            Long blogPostId,
            ReviseBlogPostWithAiRequest request
    ) {
    }
}
