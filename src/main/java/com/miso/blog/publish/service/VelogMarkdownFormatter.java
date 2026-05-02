package com.miso.blog.publish.service;

import com.miso.blog.post.entity.BlogPostEntity;
import org.springframework.stereotype.Component;

@Component
public class VelogMarkdownFormatter {
    public String buildMarkdown(
            BlogPostEntity blogPost,
            String canonicalUrl,
            boolean includeCanonicalLink,
            boolean includeSourceNote
    ) {
        StringBuilder builder = new StringBuilder();
        if (includeCanonicalLink && canonicalUrl != null && !canonicalUrl.isBlank()) {
            builder.append("> 원본 글: [")
                    .append(blogPost.getTitle())
                    .append("](")
                    .append(canonicalUrl)
                    .append(")\n\n");
        }

        builder.append(blogPost.getContentMarkdown() == null ? "" : blogPost.getContentMarkdown().strip()).append('\n');

        if (includeSourceNote) {
            builder.append("\n---\n");
            builder.append("이 글은 개인 블로그 원본 글을 Velog 노출용으로 옮긴 글입니다.\n");
        }
        return builder.toString();
    }
}
