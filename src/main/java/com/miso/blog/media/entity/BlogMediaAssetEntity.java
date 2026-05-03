package com.miso.blog.media.entity;

import com.miso.blog.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "blog_media_assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogMediaAssetEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 300)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 300)
    private String storedFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "relative_path", nullable = false, length = 500)
    private String relativePath;

    @Column(name = "public_url", nullable = false, length = 1000)
    private String publicUrl;

    @Column(name = "upload_group_id", length = 80)
    private String uploadGroupId;

    @Column(name = "alt_text", length = 500)
    private String altText;

    @Column(length = 1000)
    private String note;

    @Builder
    public BlogMediaAssetEntity(
            String originalFilename,
            String storedFilename,
            String contentType,
            long fileSize,
            String relativePath,
            String publicUrl,
            String uploadGroupId,
            String altText,
            String note
    ) {
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.relativePath = relativePath;
        this.publicUrl = publicUrl;
        this.uploadGroupId = uploadGroupId;
        this.altText = altText;
        this.note = note;
    }
}
