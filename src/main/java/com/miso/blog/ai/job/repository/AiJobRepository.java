package com.miso.blog.ai.job.repository;

import com.miso.blog.ai.job.entity.AiJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiJobRepository extends JpaRepository<AiJobEntity, Long> {
    List<AiJobEntity> findAllByOrderByIdDesc();
}
