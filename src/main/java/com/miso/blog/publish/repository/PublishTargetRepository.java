package com.miso.blog.publish.repository;

import com.miso.blog.publish.entity.PublishTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublishTargetRepository extends JpaRepository<PublishTargetEntity, Long> {
    List<PublishTargetEntity> findAllByOrderByRoleAscChannelAscIdAsc();
}
