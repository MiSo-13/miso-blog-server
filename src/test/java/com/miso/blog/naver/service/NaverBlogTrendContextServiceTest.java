package com.miso.blog.naver.service;

import com.miso.blog.naver.dto.NaverBlogSearchItem;
import com.miso.blog.naver.dto.NaverBlogSearchResult;
import com.miso.blog.post.code.GeneralBlogCategory;
import com.miso.blog.post.code.BlogPostStatus;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.reference.service.BlogReferenceContentFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NaverBlogTrendContextServiceTest {
    @Test
    void buildTrendContextUsesCategoryKeywordAndFetchedExcerpts() {
        NaverBlogSearchClient client = mock(NaverBlogSearchClient.class);
        BlogReferenceContentFetcher fetcher = mock(BlogReferenceContentFetcher.class);
        NaverBlogTrendContextService service = new NaverBlogTrendContextService(client, fetcher);
        ReflectionTestUtils.setField(service, "contentFetchLimit", 1);

        when(client.searchBlogs("성수 라비올리 서울 성동구 성수 맛집 파스타 데이트 트러플 맛집 후기"))
                .thenReturn(new NaverBlogSearchResult(
                        "성수 라비올리 서울 성동구 성수 맛집 파스타 데이트 트러플 맛집 후기",
                        "sim",
                        5,
                        true,
                        true,
                        List.of(new NaverBlogSearchItem(
                                "성수 파스타 맛집 후기",
                                "https://blog.naver.com/example/1",
                                "공간 분위기와 메뉴 후기를 나눠 쓴 글",
                                "미소",
                                "https://blog.naver.com/example",
                                "20260504"
                        )),
                        null
                ));
        when(fetcher.fetch("https://blog.naver.com/example/1"))
                .thenReturn(new BlogReferenceContentFetcher.FetchedReferenceContent(
                        "https://blog.naver.com/example/1",
                        "https://blog.naver.com/example/1",
                        200,
                        true,
                        "성수 파스타 맛집 후기",
                        "사진과 메뉴 흐름이 자연스러운 후기",
                        List.of("도입부에서 방문 목적을 먼저 밝히고 공간 분위기를 이어서 설명했습니다."),
                        null
                ));

        String context = service.buildTrendContext(new CreateGeneralBlogPostRequest(
                GeneralBlogCategory.RESTAURANT,
                "성수 파스타 맛집",
                "성수 라비올리",
                "서울 성동구 성수동",
                List.of(),
                "주말 저녁 방문",
                List.of("성수 맛집", "파스타", "데이트", "트러플"),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                false
        ));

        assertTrue(context.contains("네이버 블로그 상위 글 실시간 참고"));
        assertTrue(context.contains("성수 파스타 맛집 후기"));
        assertTrue(context.contains("도입부에서 방문 목적을 먼저 밝히고"));
        assertTrue(context.contains("조회수/좋아요 기반 인기 순위가 아니라"));
    }

    @Test
    void buildTrendContextFallsBackWhenNaverCredentialsAreMissing() {
        NaverBlogSearchClient client = mock(NaverBlogSearchClient.class);
        BlogReferenceContentFetcher fetcher = mock(BlogReferenceContentFetcher.class);
        NaverBlogTrendContextService service = new NaverBlogTrendContextService(client, fetcher);
        when(client.searchBlogs("블로그 후기"))
                .thenReturn(NaverBlogSearchResult.disabled("블로그 후기", "NAVER_CLIENT_ID 또는 NAVER_CLIENT_SECRET이 설정되어 있지 않습니다."));

        String context = service.buildTrendContext(new CreateGeneralBlogPostRequest(
                GeneralBlogCategory.ETC,
                null,
                null,
                null,
                List.of(),
                "메모",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                false
        ));

        assertTrue(context.contains("검색 결과 사용 불가"));
        assertTrue(context.contains("NAVER_CLIENT_ID"));
    }

    @Test
    void buildTrendContextSkipsDevelopmentBlogPost() {
        NaverBlogSearchClient client = mock(NaverBlogSearchClient.class);
        BlogReferenceContentFetcher fetcher = mock(BlogReferenceContentFetcher.class);
        NaverBlogTrendContextService service = new NaverBlogTrendContextService(client, fetcher);
        BlogPostEntity blogPost = BlogPostEntity.builder()
                .title("Spring Boot GitHub Pages 발행")
                .slug("spring-github-pages")
                .summary("개발 기록")
                .contentMarkdown("본문")
                .tagsJson("[]")
                .sourceNote("GitHub 분석 기반 블로그 작성 결과")
                .status(BlogPostStatus.APPROVED)
                .currentVersionNo(1)
                .build();

        String context = service.buildTrendContext(blogPost, List.of("Spring Boot"));

        assertTrue(context.contains("검색 생략"));
        verify(client, never()).searchBlogs(org.mockito.ArgumentMatchers.anyString());
    }
}
