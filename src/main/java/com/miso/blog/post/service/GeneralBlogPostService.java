package com.miso.blog.post.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.dto.GeneratedBlogDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GeneralBlogPostService {
    private final OpenAiGeneralBlogDraftComposer openAiGeneralBlogDraftComposer;
    private final BlogPostService blogPostService;

    @Transactional
    public BlogPostResponse createAiDraft(CreateGeneralBlogPostRequest request) {
        validateRequest(request);
        GeneratedBlogDraft draft = openAiGeneralBlogDraftComposer.compose(request);
        return blogPostService.createDraftAndMaybeReviewReady(new CreateBlogPostRequest(
                draft.title(),
                null,
                draft.summary(),
                draft.contentMarkdown(),
                draft.tags(),
                buildSourceNote(request, draft)
        ), Boolean.TRUE.equals(request.markReviewReady()));
    }

    private void validateRequest(CreateGeneralBlogPostRequest request) {
        boolean hasMemo = request.memo() != null && !request.memo().isBlank();
        boolean hasKeywords = request.keywords() != null && request.keywords().stream().anyMatch(value -> value != null && !value.isBlank());
        boolean hasRequiredPhrases = request.requiredPhrases() != null && request.requiredPhrases().stream().anyMatch(value -> value != null && !value.isBlank());
        boolean hasPhotos = request.photos() != null && !request.photos().isEmpty();
        if (!hasMemo && !hasKeywords && !hasRequiredPhrases && !hasPhotos) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "메모, 키워드, 필수 문구, 사진 설명 중 하나 이상은 입력해야 합니다.");
        }
    }

    private String buildSourceNote(CreateGeneralBlogPostRequest request, GeneratedBlogDraft draft) {
        return "일반 블로그 AI 작성 결과. category=%s, model=%s, titleHint=%s"
                .formatted(
                        request.category(),
                        draft.modelName(),
                        request.titleHint() == null || request.titleHint().isBlank() ? "(없음)" : request.titleHint().trim()
                );
    }
}
