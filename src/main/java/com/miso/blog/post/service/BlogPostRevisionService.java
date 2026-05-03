package com.miso.blog.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.GeneratedBlogDraft;
import com.miso.blog.post.dto.ReviseBlogPostWithAiRequest;
import com.miso.blog.post.dto.UpdateBlogPostRequest;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogPostRevisionService {
    private final BlogPostRepository blogPostRepository;
    private final BlogPostService blogPostService;
    private final OpenAiBlogRevisionComposer openAiBlogRevisionComposer;
    private final ObjectMapper objectMapper;

    @Transactional
    public BlogPostResponse reviseWithAi(Long blogPostId, ReviseBlogPostWithAiRequest request) {
        BlogPostEntity blogPost = blogPostRepository.findById(blogPostId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
        if (blogPost.getStatus() == BlogPostStatus.PUBLISHED) {
            throw new GeneralException(ErrorCode.CONFLICT, "발행된 글은 AI 수정으로 덮어쓸 수 없습니다.");
        }

        List<String> currentTags = readTags(blogPost.getTagsJson());
        GeneratedBlogDraft draft = openAiBlogRevisionComposer.revise(blogPost, currentTags, request);
        BlogPostResponse updated = blogPostService.updateDraft(blogPostId, new UpdateBlogPostRequest(
                Boolean.TRUE.equals(request.preserveTitle()) ? blogPost.getTitle() : draft.title(),
                blogPost.getSlug(),
                draft.summary(),
                draft.contentMarkdown(),
                Boolean.TRUE.equals(request.preserveTags()) ? currentTags : draft.tags(),
                appendRevisionNote(blogPost.getSourceNote(), request, draft)
        ));

        if (Boolean.TRUE.equals(request.markReviewReady())) {
            return blogPostService.markReviewReady(updated.id());
        }
        return updated;
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

    private String appendRevisionNote(String sourceNote, ReviseBlogPostWithAiRequest request, GeneratedBlogDraft draft) {
        String baseNote = sourceNote == null || sourceNote.isBlank() ? "" : sourceNote.trim() + "\n";
        String instruction = request.revisionInstruction().trim();
        if (instruction.length() > 300) {
            instruction = instruction.substring(0, 300);
        }
        return baseNote + "AI 추가 수정 반영. model=%s, instruction=%s".formatted(draft.modelName(), instruction);
    }
}
