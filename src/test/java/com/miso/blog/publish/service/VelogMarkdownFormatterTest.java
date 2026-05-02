package com.miso.blog.publish.service;

import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.entity.BlogPostEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelogMarkdownFormatterTest {
    private final VelogMarkdownFormatter formatter = new VelogMarkdownFormatter();

    @Test
    void buildMarkdownAddsCanonicalLinkWhenRequested() {
        String markdown = formatter.buildMarkdown(
                blogPost(),
                "https://blog.example.com/2026/05/02/post.html",
                true,
                true
        );

        assertTrue(markdown.startsWith("> 원본 글:"));
        assertTrue(markdown.contains("# 본문"));
        assertTrue(markdown.contains("Velog 노출용"));
    }

    @Test
    void buildMarkdownCanSkipCanonicalLink() {
        String markdown = formatter.buildMarkdown(
                blogPost(),
                "https://blog.example.com/2026/05/02/post.html",
                false,
                false
        );

        assertFalse(markdown.contains("원본 글"));
        assertFalse(markdown.contains("Velog 노출용"));
        assertTrue(markdown.startsWith("# 본문"));
    }

    private BlogPostEntity blogPost() {
        return BlogPostEntity.builder()
                .title("테스트 글")
                .slug("test-post")
                .summary("요약")
                .contentMarkdown("# 본문")
                .tagsJson("[]")
                .sourceNote("test")
                .status(BlogPostStatus.PUBLISHED)
                .currentVersionNo(1)
                .build();
    }
}
