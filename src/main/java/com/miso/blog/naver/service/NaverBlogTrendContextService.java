package com.miso.blog.naver.service;

import com.miso.blog.naver.dto.NaverBlogSearchItem;
import com.miso.blog.naver.dto.NaverBlogSearchResult;
import com.miso.blog.post.code.GeneralBlogCategory;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.entity.BlogPostEntity;
import com.miso.blog.reference.service.BlogReferenceContentFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NaverBlogTrendContextService {
    private static final int CONTEXT_MAX_LENGTH = 10000;
    private static final int MAX_QUERY_LENGTH = 80;
    private static final Pattern GENERAL_CATEGORY_PATTERN = Pattern.compile("category=([A-Z_]+)");

    private final NaverBlogSearchClient naverBlogSearchClient;
    private final BlogReferenceContentFetcher blogReferenceContentFetcher;

    @Value("${blog.naver.blog-search-content-fetch-limit:3}")
    private int contentFetchLimit;

    public String buildTrendContext(CreateGeneralBlogPostRequest request) {
        if (request == null) {
            return "(네이버 블로그 트렌드 검색 대상 없음)";
        }
        return buildTrendContext(buildQuery(request.category(), request.placeName(), request.addressHint(), request.titleHint(), request.keywords()));
    }

    public String buildTrendContext(BlogPostEntity blogPost, List<String> tags) {
        if (blogPost == null) {
            return "(네이버 블로그 트렌드 검색 대상 없음)";
        }
        if (!isGeneralBlogSource(blogPost.getSourceNote())) {
            return "(일반 블로그 작성 결과가 아니므로 네이버 블로그 상위 글 검색 생략)";
        }
        GeneralBlogCategory category = parseCategory(blogPost.getSourceNote());
        return buildTrendContext(buildQuery(category, null, null, blogPost.getTitle(), tags));
    }

    private String buildTrendContext(String query) {
        NaverBlogSearchResult result = naverBlogSearchClient.searchBlogs(query);
        StringBuilder builder = new StringBuilder();
        builder.append("네이버 블로그 상위 글 실시간 참고:\n");
        builder.append("- 네이버 공식 블로그 검색 API를 AI 호출 시점에 요청한 결과다.\n");
        builder.append("- 조회수/좋아요 기반 인기 순위가 아니라 검색 정확도/최신성 기준 상위 노출 근사치다.\n");
        builder.append("- 원문 문장을 베끼지 말고 제목 패턴, 도입부 흐름, 소제목 구조, 사진/정보 배치만 참고한다.\n");
        builder.append("- 사용자 메모에 없는 방문 경험, 가격, 메뉴, 효능, 예약 정보는 상위 글에 있어도 현재 글의 사실처럼 쓰지 않는다.\n");
        builder.append("- 검색어: ").append(query).append('\n');
        builder.append("- 정렬: ").append(result.sort()).append(", 요청 개수: ").append(result.display()).append('\n');

        if (!result.enabled() || !result.success()) {
            builder.append("- 검색 결과 사용 불가: ").append(result.errorMessage()).append('\n');
            return builder.toString();
        }
        if (result.items().isEmpty()) {
            builder.append("- 검색 결과 없음\n");
            return builder.toString();
        }

        int fetchLimit = Math.max(0, Math.min(5, contentFetchLimit));
        int index = 1;
        for (NaverBlogSearchItem item : result.items()) {
            builder.append('\n')
                    .append(index).append(". ").append(valueOrDefault(item.title(), "(제목 없음)")).append('\n');
            builder.append("  URL: ").append(valueOrDefault(item.link(), "(없음)")).append('\n');
            if (item.bloggerName() != null && !item.bloggerName().isBlank()) {
                builder.append("  블로거: ").append(item.bloggerName()).append('\n');
            }
            if (item.postDate() != null && !item.postDate().isBlank()) {
                builder.append("  작성일: ").append(item.postDate()).append('\n');
            }
            if (item.description() != null && !item.description().isBlank()) {
                builder.append("  검색 요약: ").append(item.description()).append('\n');
            }
            if (index <= fetchLimit && item.link() != null && !item.link().isBlank()) {
                appendFetchedContent(builder, blogReferenceContentFetcher.fetch(item.link()));
            }
            index++;
            if (builder.length() >= CONTEXT_MAX_LENGTH) {
                builder.append("\n... 네이버 블로그 상위 글 컨텍스트 생략 ...");
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
        builder.append("  실제 본문 확인: 성공\n");
        if (fetchedContent.pageTitle() != null && !fetchedContent.pageTitle().isBlank()) {
            builder.append("  페이지 제목: ").append(fetchedContent.pageTitle()).append('\n');
        }
        if (fetchedContent.metaDescription() != null && !fetchedContent.metaDescription().isBlank()) {
            builder.append("  페이지 설명: ").append(fetchedContent.metaDescription()).append('\n');
        }
        if (!fetchedContent.excerpts().isEmpty()) {
            builder.append("  본문 발췌:\n");
            int index = 1;
            for (String excerpt : fetchedContent.excerpts().stream().limit(3).toList()) {
                builder.append("    ").append(index++).append(". ").append(excerpt).append('\n');
            }
        }
    }

    private String buildQuery(
            GeneralBlogCategory category,
            String placeName,
            String addressHint,
            String titleHint,
            List<String> keywords
    ) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addToken(tokens, placeName);
        addToken(tokens, compactRegion(addressHint));
        if (keywords != null) {
            keywords.stream().limit(4).forEach(keyword -> addToken(tokens, keyword));
        }
        addToken(tokens, categoryKeyword(category));
        if (tokens.size() < 2) {
            addToken(tokens, titleHint);
        }
        String query = String.join(" ", new ArrayList<>(tokens));
        return limit(query.isBlank() ? "네이버 블로그 후기" : query, MAX_QUERY_LENGTH);
    }

    private GeneralBlogCategory parseCategory(String sourceNote) {
        if (sourceNote == null || sourceNote.isBlank()) {
            return GeneralBlogCategory.ETC;
        }
        Matcher matcher = GENERAL_CATEGORY_PATTERN.matcher(sourceNote);
        if (!matcher.find()) {
            return GeneralBlogCategory.ETC;
        }
        try {
            return GeneralBlogCategory.valueOf(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            return GeneralBlogCategory.ETC;
        }
    }

    private boolean isGeneralBlogSource(String sourceNote) {
        return sourceNote != null && sourceNote.contains("일반 블로그 AI 작성 결과");
    }

    private String categoryKeyword(GeneralBlogCategory category) {
        GeneralBlogCategory safeCategory = category == null ? GeneralBlogCategory.ETC : category;
        return switch (safeCategory) {
            case RESTAURANT -> "맛집 후기";
            case CAFE -> "카페 후기";
            case TRAVEL -> "여행 후기";
            case PRODUCT_REVIEW -> "제품 리뷰";
            case DAILY -> "일상 블로그";
            case ETC -> "블로그 후기";
        };
    }

    private String compactRegion(String addressHint) {
        if (addressHint == null || addressHint.isBlank()) {
            return null;
        }
        String[] parts = addressHint.trim().split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return addressHint.trim();
    }

    private void addToken(LinkedHashSet<String> tokens, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!normalized.isBlank()) {
            tokens.add(normalized);
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).strip() + "\n... 생략 ...";
    }
}
