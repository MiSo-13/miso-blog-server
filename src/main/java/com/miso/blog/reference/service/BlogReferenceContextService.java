package com.miso.blog.reference.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.entity.BlogReferenceUrlEntity;
import com.miso.blog.reference.repository.BlogReferenceUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogReferenceContextService {
    private static final int CONTEXT_MAX_LENGTH = 12000;
    private static final int REFERENCE_FETCH_LIMIT = 5;

    private final BlogReferenceUrlRepository blogReferenceUrlRepository;
    private final BlogReferenceContentFetcher blogReferenceContentFetcher;
    private final ObjectMapper objectMapper;

    public String buildReferenceContext(BlogReferenceType... types) {
        List<BlogReferenceType> referenceTypes = types == null || types.length == 0
                ? Arrays.asList(BlogReferenceType.values())
                : Arrays.asList(types);
        List<BlogReferenceUrlEntity> references = blogReferenceUrlRepository.findTop10ByTypeInAndActiveTrueOrderByIdDesc(referenceTypes);
        if (references.isEmpty()) {
            return "(참고할 레퍼런스 URL 없음)";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("레퍼런스 URL 실제 본문 참고:\n");
        builder.append("- 아래 자료는 사용자가 저장한 레퍼런스 URL을 서버가 AI 호출 직전에 직접 요청해 추출한 일부 본문이다.\n");
        builder.append("- 성공한 URL은 제목, 메타 설명, 핵심 문단 발췌를 실제 확인된 근거로 사용한다.\n");
        builder.append("- 실패한 URL은 제목/메모/태그까지만 참고하고, 본문을 읽었다고 말하지 않는다.\n");
        builder.append("- 레퍼런스 발췌문에 없는 사실은 레퍼런스에서 확인했다고 단정하지 않는다.\n");
        builder.append("- 품질 리뷰에서는 현재 글과 레퍼런스 발췌문을 비교해 잘 반영한 점, 놓친 표현, 부정확한 단정 문장을 구체적으로 피드백한다.\n\n");

        for (BlogReferenceUrlEntity reference : references.stream().limit(REFERENCE_FETCH_LIMIT).toList()) {
            BlogReferenceContentFetcher.FetchedReferenceContent fetchedContent = blogReferenceContentFetcher.fetch(reference.getUrl());
            builder.append("- [").append(reference.getType()).append("] ")
                    .append(reference.getTitle()).append('\n');
            builder.append("  URL: ").append(reference.getUrl()).append('\n');
            if (fetchedContent.finalUrl() != null && !fetchedContent.finalUrl().equals(reference.getUrl())) {
                builder.append("  최종 URL: ").append(fetchedContent.finalUrl()).append('\n');
            }
            if (reference.getDescription() != null && !reference.getDescription().isBlank()) {
                builder.append("  메모: ").append(reference.getDescription()).append('\n');
            }
            builder.append("  태그: ").append(readTags(reference.getTagsJson())).append('\n');
            appendFetchedContent(builder, fetchedContent);
            builder.append('\n');
            if (builder.length() >= CONTEXT_MAX_LENGTH) {
                builder.append("... 레퍼런스 URL 컨텍스트 생략 ...");
                break;
            }
        }
        return limit(builder.toString(), CONTEXT_MAX_LENGTH);
    }

    private void appendFetchedContent(
            StringBuilder builder,
            BlogReferenceContentFetcher.FetchedReferenceContent fetchedContent
    ) {
        if (!fetchedContent.fetched()) {
            builder.append("  실제 본문 확인: 실패");
            if (fetchedContent.errorMessage() != null && !fetchedContent.errorMessage().isBlank()) {
                builder.append(" (").append(fetchedContent.errorMessage()).append(')');
            }
            builder.append('\n');
            return;
        }

        builder.append("  실제 본문 확인: 성공");
        if (fetchedContent.statusCode() > 0) {
            builder.append(" (status=").append(fetchedContent.statusCode()).append(')');
        }
        builder.append('\n');
        if (fetchedContent.pageTitle() != null && !fetchedContent.pageTitle().isBlank()) {
            builder.append("  페이지 제목: ").append(fetchedContent.pageTitle()).append('\n');
        }
        if (fetchedContent.metaDescription() != null && !fetchedContent.metaDescription().isBlank()) {
            builder.append("  페이지 설명: ").append(fetchedContent.metaDescription()).append('\n');
        }
        if (!fetchedContent.excerpts().isEmpty()) {
            builder.append("  본문 발췌:\n");
            int index = 1;
            for (String excerpt : fetchedContent.excerpts()) {
                builder.append("    ").append(index++).append(". ").append(excerpt).append('\n');
            }
        }
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... 생략 ...";
    }
}
