package com.miso.blog.ai.repository;

import com.miso.blog.ai.entity.AiUsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLogEntity, Long> {
}
