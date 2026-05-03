package com.miso.blog.post.repository;

import com.miso.blog.post.entity.BlogPostVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogPostVersionRepository extends JpaRepository<BlogPostVersionEntity, Long> {
    List<BlogPostVersionEntity> findAllByBlogPostIdOrderByVersionNoAsc(Long blogPostId);

    Optional<BlogPostVersionEntity> findByBlogPostIdAndVersionNo(Long blogPostId, int versionNo);
}
