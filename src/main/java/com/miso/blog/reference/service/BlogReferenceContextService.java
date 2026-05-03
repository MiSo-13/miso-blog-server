package com.miso.blog.reference.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.entity.BlogReferenceUrlEntity;
import com.miso.blog.reference.repository.BlogReferenceUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogReferenceContextService {
    private static final int CONTEXT_MAX_LENGTH = 5000;

    private final BlogReferenceUrlRepository blogReferenceUrlRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String buildReferenceContext(BlogReferenceType... types) {
        List<BlogReferenceType> referenceTypes = types == null || types.length == 0
                ? Arrays.asList(BlogReferenceType.values())
                : Arrays.asList(types);
        List<BlogReferenceUrlEntity> references = blogReferenceUrlRepository.findTop10ByTypeInAndActiveTrueOrderByIdDesc(referenceTypes);
        if (references.isEmpty()) {
            return "(참고할 레퍼런스 URL 없음)";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("레퍼런스 URL 참고:\n");
        builder.append("- 아래 URL은 사용자가 저장한 참고 자료다.\n");
        builder.append("- URL의 본문을 직접 읽은 것으로 꾸미지 말고, 제공된 제목/메모 범위에서만 참고한다.\n");
        builder.append("- 관련 있는 경우 Markdown 링크로 자연스럽게 인용하거나 참고 링크 섹션에 넣는다.\n\n");

        for (BlogReferenceUrlEntity reference : references) {
            builder.append("- [").append(reference.getType()).append("] ")
                    .append(reference.getTitle()).append('\n');
            builder.append("  URL: ").append(reference.getUrl()).append('\n');
            if (reference.getDescription() != null && !reference.getDescription().isBlank()) {
                builder.append("  메모: ").append(reference.getDescription()).append('\n');
            }
            builder.append("  태그: ").append(readTags(reference.getTagsJson())).append("\n\n");
            if (builder.length() >= CONTEXT_MAX_LENGTH) {
                builder.append("... 레퍼런스 URL 컨텍스트 생략 ...");
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... 생략 ...";
    }
}
