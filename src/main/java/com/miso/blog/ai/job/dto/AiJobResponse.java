package com.miso.blog.ai.job.dto;

import com.miso.blog.ai.job.code.AiJobStatus;
import com.miso.blog.ai.job.code.AiJobType;
import com.miso.blog.ai.job.entity.AiJobEntity;

import java.time.LocalDateTime;

public record AiJobResponse(
        Long id,
        AiJobType type,
        AiJobStatus status,
        Long resultBlogPostId,
        String resultJson,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AiJobResponse from(AiJobEntity job) {
        return new AiJobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getResultBlogPostId(),
                job.getResultJson(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
