package com.miso.blog.git.entity;

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
        name = "git_repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_git_repositories_full_name", columnNames = {"repository_full_name"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GitRepositoryEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_full_name", nullable = false, length = 200)
    private String repositoryFullName;

    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Builder
    public GitRepositoryEntity(String repositoryFullName, String defaultBranch, String description, boolean active) {
        this.repositoryFullName = repositoryFullName;
        this.defaultBranch = defaultBranch;
        this.description = description;
        this.active = active;
    }

    public void update(String repositoryFullName, String defaultBranch, String description, boolean active) {
        this.repositoryFullName = repositoryFullName;
        this.defaultBranch = defaultBranch;
        this.description = description;
        this.active = active;
    }
}
