package com.miso.blog.reference.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.dto.BlogReferenceUrlResponse;
import com.miso.blog.reference.dto.CreateBlogReferenceUrlRequest;
import com.miso.blog.reference.dto.UpdateBlogReferenceUrlRequest;
import com.miso.blog.reference.entity.BlogReferenceUrlEntity;
import com.miso.blog.reference.repository.BlogReferenceUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogReferenceUrlService {
    private final BlogReferenceUrlRepository blogReferenceUrlRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BlogReferenceUrlResponse createReferenceUrl(CreateBlogReferenceUrlRequest request) {
        BlogReferenceUrlEntity referenceUrl = blogReferenceUrlRepository.save(BlogReferenceUrlEntity.builder()
                .type(request.type())
                .title(request.title().trim())
                .url(request.url().trim())
                .description(trimToNull(request.description()))
                .tagsJson(writeTags(request.tags()))
                .active(request.active() == null || request.active())
                .build());
        return BlogReferenceUrlResponse.from(referenceUrl, objectMapper);
    }

    @Transactional(readOnly = true)
    public List<BlogReferenceUrlResponse> getReferenceUrls(BlogReferenceType type) {
        List<BlogReferenceUrlEntity> references = type == null
                ? blogReferenceUrlRepository.findAllByOrderByIdDesc()
                : blogReferenceUrlRepository.findAllByTypeOrderByIdDesc(type);
        return references.stream()
                .map(reference -> BlogReferenceUrlResponse.from(reference, objectMapper))
                .toList();
    }

    @Transactional
    public BlogReferenceUrlResponse updateReferenceUrl(Long referenceUrlId, UpdateBlogReferenceUrlRequest request) {
        BlogReferenceUrlEntity referenceUrl = getReferenceUrlOrThrow(referenceUrlId);
        referenceUrl.update(
                request.type() == null ? referenceUrl.getType() : request.type(),
                trimToNull(request.title()) == null ? referenceUrl.getTitle() : request.title().trim(),
                trimToNull(request.url()) == null ? referenceUrl.getUrl() : request.url().trim(),
                request.description() == null ? referenceUrl.getDescription() : trimToNull(request.description()),
                request.tags() == null ? referenceUrl.getTagsJson() : writeTags(request.tags()),
                request.active() == null ? referenceUrl.isActive() : request.active()
        );
        return BlogReferenceUrlResponse.from(referenceUrl, objectMapper);
    }

    @Transactional
    public void deleteReferenceUrl(Long referenceUrlId) {
        blogReferenceUrlRepository.delete(getReferenceUrlOrThrow(referenceUrlId));
    }

    private BlogReferenceUrlEntity getReferenceUrlOrThrow(Long referenceUrlId) {
        return blogReferenceUrlRepository.findById(referenceUrlId)
                .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND, "레퍼런스 URL을 찾을 수 없습니다."));
    }

    private String writeTags(List<String> tags) {
        LinkedHashSet<String> normalizedTags = new LinkedHashSet<>();
        if (tags != null) {
            tags.stream()
                    .map(this::trimToNull)
                    .filter(value -> value != null)
                    .forEach(normalizedTags::add);
        }
        try {
            return objectMapper.writeValueAsString(normalizedTags);
        } catch (JsonProcessingException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "레퍼런스 태그를 저장할 수 없습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
