package com.miso.blog.publish.service;

import com.miso.blog.post.entity.BlogPostEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class GitHubPagesPostFormatter {
    private static final DateTimeFormatter FRONT_MATTER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String buildFilePath(String contentRootPath, BlogPostEntity blogPost, LocalDateTime publishedAt) {
        String rootPath = normalizeRootPath(contentRootPath);
        return rootPath + "/" + FILE_DATE_FORMATTER.format(publishedAt) + "-" + blogPost.getSlug() + ".md";
    }

    public String buildMarkdown(BlogPostEntity blogPost, List<String> tags, LocalDateTime publishedAt) {
        StringBuilder builder = new StringBuilder();
        builder.append("---\n");
        builder.append("layout: post\n");
        builder.append("title: ").append(quoteYaml(blogPost.getTitle())).append('\n');
        builder.append("date: ").append(FRONT_MATTER_DATE_FORMATTER.format(publishedAt.atZone(java.time.ZoneId.systemDefault()))).append('\n');
        if (blogPost.getSummary() != null && !blogPost.getSummary().isBlank()) {
            builder.append("description: ").append(quoteYaml(blogPost.getSummary())).append('\n');
        }
        if (tags != null && !tags.isEmpty()) {
            builder.append("tags:\n");
            for (String tag : tags) {
                builder.append("  - ").append(quoteYaml(tag)).append('\n');
            }
        }
        builder.append("---\n\n");
        builder.append(blogPost.getContentMarkdown() == null ? "" : blogPost.getContentMarkdown().strip()).append('\n');
        return builder.toString();
    }

    public String buildExpectedPublicUrl(String baseUrl, BlogPostEntity blogPost, LocalDateTime publishedAt) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        return normalizedBaseUrl + "/" + publishedAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/" + blogPost.getSlug() + ".html";
    }

    private String normalizeRootPath(String contentRootPath) {
        if (contentRootPath == null || contentRootPath.isBlank()) {
            return "_posts";
        }
        String normalized = contentRootPath.trim().replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        return normalized.isBlank() ? "_posts" : normalized;
    }

    private String quoteYaml(String value) {
        String safeValue = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", " ");
        return "\"" + safeValue + "\"";
    }
}
