package com.miso.blog.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.dto.BlogPostQualityReviewRequest;
import com.miso.blog.post.dto.BlogPostQualityReviewResponse;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogPostQualityReviewService {
    private final BlogPostRepository blogPostRepository;
    private final OpenAiBlogQualityReviewer openAiBlogQualityReviewer;
    private final ObjectMapper objectMapper;

    public BlogPostQualityReviewResponse review(Long blogPostId, BlogPostQualityReviewRequest request) {
        BlogPostEntity blogPost = blogPostRepository.findById(blogPostId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
        return openAiBlogQualityReviewer.review(blogPost, readTags(blogPost.getTagsJson()), request);
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
}
