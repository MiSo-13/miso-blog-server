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
import com.miso.blog.post.dto.UpdateBlogPostStatusRequest;
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
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int SLUG_MAX_LENGTH = 220;
    private static final int SUMMARY_MAX_LENGTH = 1000;

    private final BlogPostRepository blogPostRepository;
    private final BlogPostVersionRepository blogPostVersionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BlogPostResponse createDraft(CreateBlogPostRequest request) {
        String title = normalizeRequiredText(request.title(), "제목이 필요합니다.", TITLE_MAX_LENGTH);
        String slug = resolveSlug(request.slug(), title, null);
        String tagsJson = writeTags(request.tags());

        BlogPostEntity blogPost = blogPostRepository.save(BlogPostEntity.builder()
                .title(title)
                .slug(slug)
                .summary(truncate(trimToNull(request.summary()), SUMMARY_MAX_LENGTH))
                .contentMarkdown(request.contentMarkdown())
                .tagsJson(tagsJson)
                .sourceNote(trimToNull(request.sourceNote()))
                .status(BlogPostStatus.DRAFT)
                .currentVersionNo(1)
                .build());

        saveVersion(blogPost, BlogPostVersionAction.CREATED);
        return BlogPostResponse.from(blogPost, objectMapper);
    }

    @Transactional
    public BlogPostResponse createDraftAndMaybeReviewReady(CreateBlogPostRequest request, boolean markReviewReady) {
        BlogPostResponse created = createDraft(request);
        if (!markReviewReady) {
            return created;
        }
        return markReviewReady(created.id());
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

        String title = normalizeRequiredText(request.title(), "제목이 필요합니다.", TITLE_MAX_LENGTH);
        String slug = resolveSlug(request.slug(), title, blogPost.getId());
        blogPost.updateDraft(
                title,
                slug,
                truncate(trimToNull(request.summary()), SUMMARY_MAX_LENGTH),
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

    @Transactional
    public BlogPostResponse updateStatus(Long blogPostId, UpdateBlogPostStatusRequest request) {
        BlogPostEntity blogPost = getBlogPostOrThrow(blogPostId);
        if (blogPost.getStatus() == request.status()) {
            return BlogPostResponse.from(blogPost, objectMapper);
        }

        // 발행 후 오탈자 수정이 필요할 때 DRAFT/APPROVED 등으로 되돌릴 수 있게 한다.
        blogPost.changeStatus(request.status());
        saveVersion(blogPost, BlogPostVersionAction.STATUS_CHANGED);
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
        if (normalized.isBlank()) {
            return null;
        }
        return truncateSlug(normalized);
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

    private String normalizeRequiredText(String value, String errorMessage, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, errorMessage);
        }
        return truncate(normalized, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String truncateSlug(String value) {
        if (value.length() <= SLUG_MAX_LENGTH) {
            return value;
        }
        String truncated = value.substring(0, SLUG_MAX_LENGTH)
                .replaceAll("-+$", "");
        return truncated.isBlank() ? null : truncated;
    }
}
