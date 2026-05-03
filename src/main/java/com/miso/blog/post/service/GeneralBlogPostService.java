package com.miso.blog.post.service;

import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.media.entity.BlogMediaAssetEntity;
import com.miso.blog.media.repository.BlogMediaAssetRepository;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.dto.GeneralBlogPhotoRequest;
import com.miso.blog.post.dto.GeneratedBlogDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeneralBlogPostService {
    private final OpenAiGeneralBlogDraftComposer openAiGeneralBlogDraftComposer;
    private final BlogPostService blogPostService;
    private final BlogMediaAssetRepository blogMediaAssetRepository;

    @Transactional
    public BlogPostResponse createAiDraft(CreateGeneralBlogPostRequest request) {
        CreateGeneralBlogPostRequest enrichedRequest = enrichPhotos(request);
        validateRequest(enrichedRequest);
        GeneratedBlogDraft draft = openAiGeneralBlogDraftComposer.compose(enrichedRequest);
        return blogPostService.createDraftAndMaybeReviewReady(new CreateBlogPostRequest(
                draft.title(),
                null,
                draft.summary(),
                draft.contentMarkdown(),
                draft.tags(),
                buildSourceNote(enrichedRequest, draft)
        ), Boolean.TRUE.equals(enrichedRequest.markReviewReady()));
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
        return "일반 블로그 AI 작성 결과. category=%s, model=%s, titleHint=%s, photoGroupId=%s"
                .formatted(
                        request.category(),
                        draft.modelName(),
                        request.titleHint() == null || request.titleHint().isBlank() ? "(없음)" : request.titleHint().trim(),
                        request.photoGroupId() == null || request.photoGroupId().isBlank() ? "(없음)" : request.photoGroupId().trim()
                );
    }

    private CreateGeneralBlogPostRequest enrichPhotos(CreateGeneralBlogPostRequest request) {
        List<GeneralBlogPhotoRequest> photos = new ArrayList<>();
        if (request.photos() != null) {
            photos.addAll(request.photos());
        }

        LinkedHashMap<Long, BlogMediaAssetEntity> assetsById = new LinkedHashMap<>();
        if (request.photoAssetIds() != null && !request.photoAssetIds().isEmpty()) {
            List<Long> ids = request.photoAssetIds().stream()
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
            if (!ids.isEmpty()) {
                List<BlogMediaAssetEntity> assets = blogMediaAssetRepository.findAllByIdInOrderByIdAsc(ids);
                if (assets.size() != ids.size()) {
                    throw new GeneralException(ErrorCode.BAD_REQUEST, "존재하지 않는 사진 assetId가 포함되어 있습니다.");
                }
                assets.forEach(asset -> assetsById.put(asset.getId(), asset));
            }
        }

        String photoGroupId = trimToNull(request.photoGroupId());
        if (photoGroupId != null) {
            List<BlogMediaAssetEntity> groupedAssets = blogMediaAssetRepository.findAllByUploadGroupIdOrderByIdAsc(photoGroupId);
            if (groupedAssets.isEmpty()) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "사진 묶음을 찾을 수 없습니다.");
            }
            groupedAssets.forEach(asset -> assetsById.putIfAbsent(asset.getId(), asset));
        }

        assetsById.values()
                .stream()
                .map(this::toPhotoRequest)
                .forEach(photos::add);

        return new CreateGeneralBlogPostRequest(
                request.category(),
                request.titleHint(),
                request.placeName(),
                request.addressHint(),
                request.requiredPhrases(),
                request.memo(),
                request.keywords(),
                photos,
                request.photoAssetIds(),
                request.photoGroupId(),
                request.imagePlacementNotes(),
                request.tone(),
                request.audience(),
                request.targetLength(),
                request.markReviewReady()
        );
    }

    private GeneralBlogPhotoRequest toPhotoRequest(BlogMediaAssetEntity asset) {
        String description = choose(asset.getAltText(), asset.getNote(), asset.getOriginalFilename());
        return new GeneralBlogPhotoRequest(
                asset.getPublicUrl(),
                description,
                asset.getNote()
        );
    }

    private String choose(String first, String second, String fallback) {
        String firstValue = trimToNull(first);
        if (firstValue != null) {
            return firstValue;
        }
        String secondValue = trimToNull(second);
        return secondValue == null ? fallback : secondValue;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
