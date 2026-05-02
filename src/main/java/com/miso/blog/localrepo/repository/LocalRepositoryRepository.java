package com.miso.blog.localrepo.repository;

import com.miso.blog.localrepo.entity.LocalRepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocalRepositoryRepository extends JpaRepository<LocalRepositoryEntity, Long> {
    List<LocalRepositoryEntity> findAllByOrderByIdDesc();

    boolean existsByLocalPath(String localPath);

    boolean existsByLocalPathAndIdNot(String localPath, Long id);
}
