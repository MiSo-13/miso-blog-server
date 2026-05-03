package com.miso.blog.ai.job.entity;

import com.miso.blog.ai.job.code.AiJobFailureCode;
import com.miso.blog.ai.job.code.AiJobStatus;
import com.miso.blog.ai.job.code.AiJobType;
import com.miso.blog.ai.job.service.AiJobFailure;
import com.miso.blog.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ai_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJobEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiJobStatus status;

    @Lob
    @Column(name = "request_json", nullable = false, columnDefinition = "LONGTEXT")
    private String requestJson;

    @Lob
    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "result_blog_post_id")
    private Long resultBlogPostId;

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 80)
    private AiJobFailureCode failureCode;

    @Lob
    @Column(name = "failure_detail_message", columnDefinition = "TEXT")
    private String failureDetailMessage;

    @Column(name = "failure_retryable")
    private Boolean failureRetryable;

    @Column(name = "failure_action_guide", length = 1000)
    private String failureActionGuide;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "retried_from_job_id")
    private Long retriedFromJobId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Builder
    public AiJobEntity(AiJobType type, AiJobStatus status, String requestJson, Integer retryCount, Long retriedFromJobId) {
        this.type = type;
        this.status = status;
        this.requestJson = requestJson;
        this.retryCount = retryCount == null ? 0 : retryCount;
        this.retriedFromJobId = retriedFromJobId;
    }

    public void markRunning() {
        this.status = AiJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
        this.failureCode = null;
        this.failureDetailMessage = null;
        this.failureRetryable = null;
        this.failureActionGuide = null;
    }

    public void markSucceeded(String resultJson, Long resultBlogPostId) {
        this.status = AiJobStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.resultBlogPostId = resultBlogPostId;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
        this.failureCode = null;
        this.failureDetailMessage = null;
        this.failureRetryable = null;
        this.failureActionGuide = null;
    }

    public void markFailed(AiJobFailure failure) {
        this.status = AiJobStatus.FAILED;
        this.errorMessage = failure.message();
        this.failureCode = failure.code();
        this.failureDetailMessage = failure.detailMessage();
        this.failureRetryable = failure.retryable();
        this.failureActionGuide = failure.actionGuide();
        this.finishedAt = LocalDateTime.now();
    }
}
