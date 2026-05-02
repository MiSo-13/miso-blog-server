package com.miso.blog.post.entity;

import com.miso.blog.common.entity.BaseTimeEntity;
import com.miso.blog.post.code.BlogPostVersionAction;
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
@Table(name = "blog_post_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogPostVersionEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_post_id", nullable = false)
    private BlogPostEntity blogPost;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BlogPostVersionAction action;

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

    @Builder
    public BlogPostVersionEntity(
            BlogPostEntity blogPost,
            int versionNo,
            BlogPostVersionAction action,
            String title,
            String slug,
            String summary,
            String contentMarkdown,
            String tagsJson
    ) {
        this.blogPost = blogPost;
        this.versionNo = versionNo;
        this.action = action;
        this.title = title;
        this.slug = slug;
        this.summary = summary;
        this.contentMarkdown = contentMarkdown;
        this.tagsJson = tagsJson;
    }
}
