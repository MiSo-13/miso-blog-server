package com.miso.blog.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.security.SecretMaskingService;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogPostMemoryContextService {
    private static final int CONTENT_SNIPPET_MAX_LENGTH = 900;
    private static final int CONTEXT_MAX_LENGTH = 6000;

    private final BlogPostRepository blogPostRepository;
    private final ObjectMapper objectMapper;
    private final SecretMaskingService secretMaskingService;

    @Transactional(readOnly = true)
    public String buildRecentPostContext(Long currentBlogPostId) {
        List<BlogPostEntity> posts = currentBlogPostId == null
                ? blogPostRepository.findAllByOrderByIdDesc().stream().limit(5).toList()
                : blogPostRepository.findTop5ByIdNotOrderByIdDesc(currentBlogPostId);
        if (posts.isEmpty()) {
            return "(참고할 이전 저장 글 없음)";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("이전 저장 글 참고 컨텍스트:\n");
        builder.append("- 아래 내용은 사용자의 기존 블로그 문체, 구성, 자주 쓰는 태그를 참고하기 위한 자료다.\n");
        builder.append("- 새 글의 사실관계보다 우선하지 않는다.\n");
        builder.append("- 기존 글의 표현을 그대로 복사하지 말고 톤과 구성만 참고한다.\n\n");

        for (BlogPostEntity post : posts) {
            builder.append("## ").append(post.getTitle()).append('\n');
            builder.append("- 상태: ").append(post.getStatus()).append('\n');
            builder.append("- 요약: ").append(valueOrDefault(post.getSummary(), "(없음)")).append('\n');
            builder.append("- 태그: ").append(readTags(post.getTagsJson())).append('\n');
            builder.append("- 본문 일부:\n");
            builder.append(limit(secretMaskingService.mask(valueOrDefault(post.getContentMarkdown(), "")), CONTENT_SNIPPET_MAX_LENGTH));
            builder.append("\n\n");
            if (builder.length() >= CONTEXT_MAX_LENGTH) {
                builder.append("... 이전 글 컨텍스트 생략 ...");
                break;
            }
        }
        return limit(builder.toString(), CONTEXT_MAX_LENGTH);
    }

    private List<String> readTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... 생략 ...";
    }
}
