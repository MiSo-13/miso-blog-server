package com.miso.blog.localrepo.repository;

import com.miso.blog.localrepo.entity.LocalRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocalRepositoryRepository extends JpaRepository<LocalRepositoryEntity, Long> {
    List<LocalRepositoryEntity> findAllByOrderByIdDesc();

    Optional<LocalRepositoryEntity> findByLocalPath(String localPath);

    boolean existsByLocalPath(String localPath);

    boolean existsByLocalPathAndIdNot(String localPath, Long id);
}
