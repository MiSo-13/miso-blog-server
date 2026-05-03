package com.miso.blog.ai.job.entity;

import com.miso.blog.ai.job.code.AiJobStatus;
import com.miso.blog.ai.job.code.AiJobType;
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

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Builder
    public AiJobEntity(AiJobType type, AiJobStatus status, String requestJson) {
        this.type = type;
        this.status = status;
        this.requestJson = requestJson;
    }

    public void markRunning() {
        this.status = AiJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markSucceeded(String resultJson, Long resultBlogPostId) {
        this.status = AiJobStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.resultBlogPostId = resultBlogPostId;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = AiJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }
}
