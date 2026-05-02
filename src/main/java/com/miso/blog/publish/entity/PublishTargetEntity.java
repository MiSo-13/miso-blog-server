package com.miso.blog.publish.entity;

import com.miso.blog.common.entity.BaseTimeEntity;
import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "publish_targets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishTargetEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PublishChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PublishRole role;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "repository_full_name", length = 200)
    private String repositoryFullName;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Column(name = "content_root_path", length = 300)
    private String contentRootPath;

    @Column(name = "custom_domain", length = 200)
    private String customDomain;

    @Column(nullable = false)
    private boolean active;

    @Builder
    public PublishTargetEntity(
            PublishChannel channel,
            PublishRole role,
            String name,
            String baseUrl,
            String repositoryFullName,
            String branchName,
            String contentRootPath,
            String customDomain,
            boolean active
    ) {
        this.channel = channel;
        this.role = role;
        this.name = name;
        this.baseUrl = baseUrl;
        this.repositoryFullName = repositoryFullName;
        this.branchName = branchName;
        this.contentRootPath = contentRootPath;
        this.customDomain = customDomain;
        this.active = active;
    }

    public void update(
            PublishRole role,
            String name,
            String baseUrl,
            String repositoryFullName,
            String branchName,
            String contentRootPath,
            String customDomain,
            boolean active
    ) {
        this.role = role;
        this.name = name;
        this.baseUrl = baseUrl;
        this.repositoryFullName = repositoryFullName;
        this.branchName = branchName;
        this.contentRootPath = contentRootPath;
        this.customDomain = customDomain;
        this.active = active;
    }
}
