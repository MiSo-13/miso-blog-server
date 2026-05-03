package com.miso.blog.reference.entity;

import com.miso.blog.common.entity.BaseTimeEntity;
import com.miso.blog.reference.code.BlogReferenceType;
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

@Getter
@Entity
@Table(name = "blog_reference_urls")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogReferenceUrlEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BlogReferenceType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 1000)
    private String description;

    @Lob
    @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
    private String tagsJson;

    @Column(nullable = false)
    private boolean active;

    @Builder
    public BlogReferenceUrlEntity(
            BlogReferenceType type,
            String title,
            String url,
            String description,
            String tagsJson,
            boolean active
    ) {
        this.type = type;
        this.title = title;
        this.url = url;
        this.description = description;
        this.tagsJson = tagsJson;
        this.active = active;
    }

    public void update(
            BlogReferenceType type,
            String title,
            String url,
            String description,
            String tagsJson,
            boolean active
    ) {
        this.type = type;
        this.title = title;
        this.url = url;
        this.description = description;
        this.tagsJson = tagsJson;
        this.active = active;
    }
}
