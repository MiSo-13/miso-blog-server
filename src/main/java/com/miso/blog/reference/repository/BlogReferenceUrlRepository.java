package com.miso.blog.reference.repository;

import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.entity.BlogReferenceUrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BlogReferenceUrlRepository extends JpaRepository<BlogReferenceUrlEntity, Long> {
    List<BlogReferenceUrlEntity> findAllByOrderByIdDesc();

    List<BlogReferenceUrlEntity> findAllByTypeOrderByIdDesc(BlogReferenceType type);

    List<BlogReferenceUrlEntity> findTop10ByTypeInAndActiveTrueOrderByIdDesc(Collection<BlogReferenceType> types);
}
