package com.miso.blog.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.code.BlogPostVersionAction;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.BlogPostSummaryResponse;
import com.miso.blog.post.dto.BlogPostVersionResponse;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.dto.UpdateBlogPostRequest;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.post.entity.BlogPostVersionEntity;
import com.miso.blog.post.repository.BlogPostRepository;
import com.miso.blog.post.repository.BlogPostVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BlogPostService {
    private static final DateTimeFormatter SLUG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BlogPostRepository blogPostRepository;
    private final BlogPostVersionRepository blogPostVersionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BlogPostResponse createDraft(CreateBlogPostRequest request) {
        String slug = resolveSlug(request.slug(), request.title(), null);
        String tagsJson = writeTags(request.tags());

        BlogPostEntity blogPost = blogPostRepository.save(BlogPostEntity.builder()
                .title(request.title().trim())
                .slug(slug)
                .summary(trimToNull(request.summary()))
                .contentMarkdown(request.contentMarkdown())
                .tagsJson(tagsJson)
                .sourceNote(trimToNull(request.sourceNote()))
                .status(BlogPostStatus.DRAFT)
                .currentVersionNo(1)
                .build());

        saveVersion(blogPost, BlogPostVersionAction.CREATED);
        return BlogPostResponse.from(blogPost, objectMapper);
    }

    @Transactional(readOnly = true)
    public List<BlogPostSummaryResponse> getBlogPosts() {
        return blogPostRepository.findAllByOrderByIdDesc()
                .stream()
                .map(blogPost -> BlogPostSummaryResponse.from(blogPost, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public BlogPostResponse getBlogPost(Long blogPostId) {
        return BlogPostResponse.from(getBlogPostOrThrow(blogPostId), objectMapper);
    }

    @Transactional(readOnly = true)
    public List<BlogPostVersionResponse> getVersions(Long blogPostId) {
        getBlogPostOrThrow(blogPostId);
        return blogPostVersionRepository.findAllByBlogPostIdOrderByVersionNoAsc(blogPostId)
                .stream()
                .map(BlogPostVersionResponse::from)
                .toList();
    }

    @Transactional
    public BlogPostResponse updateDraft(Long blogPostId, UpdateBlogPostRequest request) {
        BlogPostEntity blogPost = getBlogPostOrThrow(blogPostId);
        if (blogPost.getStatus() == BlogPostStatus.PUBLISHED) {
            throw new GeneralException(ErrorCode.CONFLICT, "발행된 글은 초안 수정 API로 수정할 수 없습니다.");
        }

        String slug = resolveSlug(request.slug(), request.title(), blogPost.getId());
        blogPost.updateDraft(
                request.title().trim(),
                slug,
                trimToNull(request.summary()),
                request.contentMarkdown(),
                writeTags(request.tags()),
                trimToNull(request.sourceNote())
        );
        saveVersion(blogPost, BlogPostVersionAction.UPDATED);
        return BlogPostResponse.from(blogPost, objectMapper);
    }

    @Transactional
    public BlogPostResponse markReviewReady(Long blogPostId) {
        BlogPostEntity blogPost = getBlogPostOrThrow(blogPostId);
        blogPost.markReviewReady();
        saveVersion(blogPost, BlogPostVersionAction.REVIEW_READY);
        return BlogPostResponse.from(blogPost, objectMapper);
    }

    @Transactional
    public BlogPostResponse approve(Long blogPostId) {
        BlogPostEntity blogPost = getBlogPostOrThrow(blogPostId);
        if (blogPost.getStatus() != BlogPostStatus.REVIEW_READY && blogPost.getStatus() != BlogPostStatus.DRAFT) {
            throw new GeneralException(ErrorCode.CONFLICT, "검수 대기 또는 초안 상태의 글만 승인할 수 있습니다.");
        }

        blogPost.approve();
        saveVersion(blogPost, BlogPostVersionAction.APPROVED);
        return BlogPostResponse.from(blogPost, objectMapper);
    }

    @Transactional
    public BlogPostResponse markPublished(Long blogPostId) {
        BlogPostEntity blogPost = getBlogPostOrThrow(blogPostId);
        if (blogPost.getStatus() != BlogPostStatus.APPROVED) {
            throw new GeneralException(ErrorCode.CONFLICT, "승인된 글만 발행 완료 처리할 수 있습니다.");
        }

        blogPost.markPublished();
        saveVersion(blogPost, BlogPostVersionAction.PUBLISHED);
        return BlogPostResponse.from(blogPost, objectMapper);
    }

    private BlogPostEntity getBlogPostOrThrow(Long blogPostId) {
        return blogPostRepository.findById(blogPostId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
    }

    private void saveVersion(BlogPostEntity blogPost, BlogPostVersionAction action) {
        blogPostVersionRepository.save(BlogPostVersionEntity.builder()
                .blogPost(blogPost)
                .versionNo(blogPost.getCurrentVersionNo())
                .action(action)
                .title(blogPost.getTitle())
                .slug(blogPost.getSlug())
                .summary(blogPost.getSummary())
                .contentMarkdown(blogPost.getContentMarkdown())
                .tagsJson(blogPost.getTagsJson())
                .build());
    }

    private String resolveSlug(String requestedSlug, String title, Long currentPostId) {
        String slug = normalizeSlug(requestedSlug);
        if (slug == null) {
            slug = normalizeSlug(title);
        }
        if (slug == null) {
            slug = "post-" + LocalDateTime.now().format(SLUG_TIME_FORMATTER);
        }

        boolean duplicated = currentPostId == null
                ? blogPostRepository.existsBySlug(slug)
                : blogPostRepository.existsBySlugAndIdNot(slug, currentPostId);
        if (duplicated) {
            throw new GeneralException(ErrorCode.CONFLICT, "이미 사용 중인 slug입니다.");
        }
        return slug;
    }

    private String normalizeSlug(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(rawValue.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("[^a-z0-9가-힣]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String writeTags(List<String> tags) {
        LinkedHashSet<String> normalizedTags = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                String normalizedTag = trimToNull(tag);
                if (normalizedTag != null) {
                    normalizedTags.add(normalizedTag);
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(normalizedTags);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "태그를 저장할 수 없습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
