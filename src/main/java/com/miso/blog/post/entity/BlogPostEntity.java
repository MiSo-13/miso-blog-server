package com.miso.blog.post.entity;

import com.miso.blog.common.entity.BaseTimeEntity;
import com.miso.blog.post.code.BlogPostStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "blog_posts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_blog_posts_slug", columnNames = {"slug"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogPostEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(length = 1000)
    private String summary;

    @Lob
    @Column(name = "content_markdown", nullable = false, columnDefinition = "LONGTEXT")
    private String contentMarkdown;

    @Lob
    @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
    private String tagsJson;

    @Lob
    @Column(name = "source_note", columnDefinition = "LONGTEXT")
    private String sourceNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BlogPostStatus status;

    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder
    public BlogPostEntity(
            String title,
            String slug,
            String summary,
            String contentMarkdown,
            String tagsJson,
            String sourceNote,
            BlogPostStatus status,
            int currentVersionNo
    ) {
        this.title = title;
        this.slug = slug;
        this.summary = summary;
        this.contentMarkdown = contentMarkdown;
        this.tagsJson = tagsJson;
        this.sourceNote = sourceNote;
        this.status = status;
        this.currentVersionNo = currentVersionNo;
    }

    public void updateDraft(String title, String slug, String summary, String contentMarkdown, String tagsJson, String sourceNote) {
        this.title = title;
        this.slug = slug;
        this.summary = summary;
        this.contentMarkdown = contentMarkdown;
        this.tagsJson = tagsJson;
        this.sourceNote = sourceNote;
        this.currentVersionNo += 1;
        this.status = BlogPostStatus.DRAFT;
        this.approvedAt = null;
        this.publishedAt = null;
    }

    public void markReviewReady() {
        this.status = BlogPostStatus.REVIEW_READY;
    }

    public void approve() {
        this.status = BlogPostStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    // 실제 외부 발행 연동 전까지는 상태 전환만 담당합니다.
    public void markPublished() {
        this.status = BlogPostStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
}
