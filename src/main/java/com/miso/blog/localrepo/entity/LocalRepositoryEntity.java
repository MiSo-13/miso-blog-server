package com.miso.blog.localrepo.entity;

import com.miso.blog.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "local_repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_local_repositories_path", columnNames = {"local_path"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalRepositoryEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "local_path", nullable = false, length = 1000)
    private String localPath;

    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Builder
    public LocalRepositoryEntity(String name, String localPath, String defaultBranch, String description, boolean active) {
        this.name = name;
        this.localPath = localPath;
        this.defaultBranch = defaultBranch;
        this.description = description;
        this.active = active;
    }

    public void update(String name, String localPath, String defaultBranch, String description, boolean active) {
        this.name = name;
        this.localPath = localPath;
        this.defaultBranch = defaultBranch;
        this.description = description;
        this.active = active;
    }
}
