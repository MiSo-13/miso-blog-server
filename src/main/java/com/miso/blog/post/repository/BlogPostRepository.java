package com.miso.blog.post.repository;

import com.miso.blog.post.entity.BlogPostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPostEntity, Long> {
    List<BlogPostEntity> findAllByOrderByIdDesc();

    List<BlogPostEntity> findTop5ByIdNotOrderByIdDesc(Long id);

    Optional<BlogPostEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
