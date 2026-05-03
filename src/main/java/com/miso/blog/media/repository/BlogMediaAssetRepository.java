package com.miso.blog.media.repository;

import com.miso.blog.media.entity.BlogMediaAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BlogMediaAssetRepository extends JpaRepository<BlogMediaAssetEntity, Long> {
    List<BlogMediaAssetEntity> findAllByOrderByIdDesc();

    List<BlogMediaAssetEntity> findAllByIdInOrderByIdAsc(Collection<Long> ids);

    List<BlogMediaAssetEntity> findAllByUploadGroupIdOrderByIdAsc(String uploadGroupId);
}
