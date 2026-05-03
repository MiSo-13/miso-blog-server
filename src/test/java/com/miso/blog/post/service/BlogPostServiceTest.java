package com.miso.blog.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.dto.UpdateBlogPostStatusRequest;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.entity.BlogPostVersionEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import com.miso.blog.post.repository.BlogPostVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void updateStatusCanMovePublishedPostBackToDraft() {
        BlogPostRepository blogPostRepository = mock(BlogPostRepository.class);
        BlogPostVersionRepository versionRepository = mock(BlogPostVersionRepository.class);
        BlogPostEntity blogPost = BlogPostEntity.builder()
                .title("Published post")
                .slug("published-post")
                .summary("summary")
                .contentMarkdown("# body")
                .tagsJson("[]")
                .status(BlogPostStatus.PUBLISHED)
                .currentVersionNo(1)
                .build();
        blogPost.markPublished();

        when(blogPostRepository.findById(1L)).thenReturn(Optional.of(blogPost));
        when(versionRepository.save(any(BlogPostVersionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BlogPostService service = new BlogPostService(blogPostRepository, versionRepository, new ObjectMapper());
        BlogPostResponse response = service.updateStatus(1L, new UpdateBlogPostStatusRequest(BlogPostStatus.DRAFT));

        assertEquals(BlogPostStatus.DRAFT, response.status());
        assertNull(response.approvedAt());
        assertNull(response.publishedAt());
    }
}
