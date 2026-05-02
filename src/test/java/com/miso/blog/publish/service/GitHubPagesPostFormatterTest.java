package com.miso.blog.publish.service;

import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.entity.BlogPostEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubPagesPostFormatterTest {
    private final GitHubPagesPostFormatter formatter = new GitHubPagesPostFormatter();

    @Test
    void buildFilePathUsesJekyllPostNaming() {
        BlogPostEntity blogPost = blogPost();

        String filePath = formatter.buildFilePath("/_posts/", blogPost, LocalDateTime.of(2026, 5, 2, 19, 0));

        assertEquals("_posts/2026-05-02-spring-openai-cost.md", filePath);
    }

    @Test
    void buildMarkdownAddsFrontMatter() {
        BlogPostEntity blogPost = blogPost();

        String markdown = formatter.buildMarkdown(
                blogPost,
                List.of("Spring Boot", "OpenAI"),
                LocalDateTime.of(2026, 5, 2, 19, 0)
        );

        assertTrue(markdown.contains("layout: post"));
        assertTrue(markdown.contains("title: \"Spring에서 OpenAI 비용 조회\""));
        assertTrue(markdown.contains("description: \"구현 기록\""));
        assertTrue(markdown.contains("  - \"Spring Boot\""));
        assertTrue(markdown.contains("# 본문"));
    }

    private BlogPostEntity blogPost() {
        return BlogPostEntity.builder()
                .title("Spring에서 OpenAI 비용 조회")
                .slug("spring-openai-cost")
                .summary("구현 기록")
                .contentMarkdown("# 본문")
                .tagsJson("[]")
                .sourceNote("test")
                .status(BlogPostStatus.APPROVED)
                .currentVersionNo(1)
                .build();
    }
}
