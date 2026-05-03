package com.miso.blog.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.entity.BlogPostVersionEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import com.miso.blog.post.repository.BlogPostVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlogPostServiceTest {
    @Test
    void createDraftTruncatesFieldsToDatabaseLengths() {
        BlogPostRepository blogPostRepository = mock(BlogPostRepository.class);
        BlogPostVersionRepository versionRepository = mock(BlogPostVersionRepository.class);
        when(blogPostRepository.existsBySlug(any())).thenReturn(false);
        when(blogPostRepository.save(any(BlogPostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any(BlogPostVersionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BlogPostService service = new BlogPostService(blogPostRepository, versionRepository, new ObjectMapper());
        String longTitle = "제목".repeat(150);
        String longSummary = "요약".repeat(700);

        BlogPostResponse response = service.createDraft(new CreateBlogPostRequest(
                longTitle,
                null,
                longSummary,
                "# 본문",
                List.of("테스트"),
                "source"
        ));

        assertEquals(200, response.title().length());
        assertEquals(1000, response.summary().length());
        assertTrue(response.slug().length() <= 220);
    }
}
