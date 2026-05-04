package com.miso.blog.reference.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.entity.BlogReferenceUrlEntity;
import com.miso.blog.reference.repository.BlogReferenceUrlRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlogReferenceContextServiceTest {
    @Test
    void buildReferenceContextIncludesFetchedPageExcerpts() {
        BlogReferenceUrlRepository repository = mock(BlogReferenceUrlRepository.class);
        BlogReferenceContentFetcher fetcher = mock(BlogReferenceContentFetcher.class);
        BlogReferenceUrlEntity reference = BlogReferenceUrlEntity.builder()
                .type(BlogReferenceType.GENERAL)
                .title("성수 카페 레퍼런스")
                .url("https://example.com/cafe")
                .description("분위기 묘사 참고")
                .tagsJson("[\"cafe\"]")
                .active(true)
                .build();
        when(repository.findTop10ByTypeInAndActiveTrueOrderByIdDesc(List.of(BlogReferenceType.GENERAL)))
                .thenReturn(List.of(reference));
        when(fetcher.fetch("https://example.com/cafe")).thenReturn(new BlogReferenceContentFetcher.FetchedReferenceContent(
                "https://example.com/cafe",
                "https://example.com/cafe",
                200,
                true,
                "성수동 조용한 카페 후기",
                "공간 분위기와 동선을 중심으로 정리한 글",
                List.of("처음 들어갔을 때 조명이 과하지 않아 오래 머물기 편한 분위기였습니다."),
                null
        ));

        BlogReferenceContextService service = new BlogReferenceContextService(repository, fetcher, new ObjectMapper());
        String context = service.buildReferenceContext(BlogReferenceType.GENERAL);

        assertTrue(context.contains("실제 본문 확인: 성공"));
        assertTrue(context.contains("성수동 조용한 카페 후기"));
        assertTrue(context.contains("처음 들어갔을 때 조명이 과하지 않아"));
    }
}
