package com.miso.blog.localrepo.repository;

import com.miso.blog.localrepo.entity.LocalRepositoryAnalysisReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocalRepositoryAnalysisReportRepository extends JpaRepository<LocalRepositoryAnalysisReportEntity, Long> {
    List<LocalRepositoryAnalysisReportEntity> findAllByLocalRepositoryIdOrderByIdDesc(Long localRepositoryId);
}
