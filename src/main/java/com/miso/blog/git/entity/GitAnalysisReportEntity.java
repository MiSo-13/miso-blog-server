package com.miso.blog.git.entity;

import com.miso.blog.common.entity.BaseTimeEntity;
import com.miso.blog.git.code.GitAnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "git_analysis_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GitAnalysisReportEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitRepositoryEntity repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GitAnalysisStatus status;

    @Column(name = "commit_limit", nullable = false)
    private int commitLimit;

    @Column(length = 2000)
    private String focus;

    @Lob
    @Column(name = "source_summary", columnDefinition = "LONGTEXT")
    private String sourceSummary;

    @Lob
    @Column(name = "analysis_summary", columnDefinition = "LONGTEXT")
    private String analysisSummary;

    @Lob
    @Column(name = "keywords_json", nullable = false, columnDefinition = "TEXT")
    private String keywordsJson;

    @Lob
    @Column(name = "topic_candidates_json", nullable = false, columnDefinition = "LONGTEXT")
    private String topicCandidatesJson;

    @Column(name = "recommended_title", length = 300)
    private String recommendedTitle;

    @Lob
    @Column(name = "draft_markdown", columnDefinition = "LONGTEXT")
    private String draftMarkdown;

    @Lob
    @Column(name = "raw_response", columnDefinition = "LONGTEXT")
    private String rawResponse;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "created_blog_post_id")
    private Long createdBlogPostId;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Builder
    public GitAnalysisReportEntity(
            GitRepositoryEntity repository,
            GitAnalysisStatus status,
            int commitLimit,
            String focus,
            String sourceSummary,
            String analysisSummary,
            String keywordsJson,
            String topicCandidatesJson,
            String recommendedTitle,
            String draftMarkdown,
            String rawResponse,
            String modelName,
            Long createdBlogPostId,
            String errorMessage
    ) {
        this.repository = repository;
        this.status = status;
        this.commitLimit = commitLimit;
        this.focus = focus;
        this.sourceSummary = sourceSummary;
        this.analysisSummary = analysisSummary;
        this.keywordsJson = keywordsJson;
        this.topicCandidatesJson = topicCandidatesJson;
        this.recommendedTitle = recommendedTitle;
        this.draftMarkdown = draftMarkdown;
        this.rawResponse = rawResponse;
        this.modelName = modelName;
        this.createdBlogPostId = createdBlogPostId;
        this.errorMessage = errorMessage;
    }

    public void connectBlogPost(Long blogPostId) {
        this.createdBlogPostId = blogPostId;
    }
}
