package com.miso.blog.git.repository;

import com.miso.blog.git.entity.GitAnalysisReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GitAnalysisReportRepository extends JpaRepository<GitAnalysisReportEntity, Long> {
    List<GitAnalysisReportEntity> findAllByRepositoryIdOrderByIdDesc(Long repositoryId);
}
