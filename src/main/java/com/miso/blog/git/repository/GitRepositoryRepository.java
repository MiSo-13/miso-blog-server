package com.miso.blog.git.repository;

import com.miso.blog.git.entity.GitRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GitRepositoryRepository extends JpaRepository<GitRepositoryEntity, Long> {
    List<GitRepositoryEntity> findAllByOrderByIdDesc();

    boolean existsByRepositoryFullName(String repositoryFullName);

    boolean existsByRepositoryFullNameAndIdNot(String repositoryFullName, Long id);
}
