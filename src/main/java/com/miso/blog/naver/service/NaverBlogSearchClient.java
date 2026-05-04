package com.miso.blog.naver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.naver.dto.NaverBlogSearchItem;
import com.miso.blog.naver.dto.NaverBlogSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class NaverBlogSearchClient {
    private static final String BLOG_SEARCH_ENDPOINT = "https://openapi.naver.com/v1/search/blog.json";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${blog.naver.client-id:}")
    private String clientId;

    @Value("${blog.naver.client-secret:}")
    private String clientSecret;

    @Value("${blog.naver.blog-search-enabled:true}")
    private boolean blogSearchEnabled;

    @Value("${blog.naver.blog-search-display:5}")
    private int blogSearchDisplay;

    @Value("${blog.naver.blog-search-sort:sim}")
    private String blogSearchSort;

    public NaverBlogSearchResult searchBlogs(String query) {
        String normalizedQuery = trimToNull(query);
        if (normalizedQuery == null) {
            return NaverBlogSearchResult.disabled("(검색어 없음)", "네이버 블로그 검색어가 비어 있습니다.");
        }
        if (!blogSearchEnabled) {
            return NaverBlogSearchResult.disabled(normalizedQuery, "네이버 블로그 검색이 비활성화되어 있습니다.");
        }
        if (trimToNull(clientId) == null || trimToNull(clientSecret) == null) {
            return NaverBlogSearchResult.disabled(normalizedQuery, "NAVER_CLIENT_ID 또는 NAVER_CLIENT_SECRET이 설정되어 있지 않습니다.");
        }

        int display = Math.max(1, Math.min(10, blogSearchDisplay));
        String sort = normalizeSort(blogSearchSort);

        try {
            URI uri = URI.create(BLOG_SEARCH_ENDPOINT
                    + "?query=" + encode(normalizedQuery)
                    + "&display=" + display
                    + "&start=1"
                    + "&sort=" + sort);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return NaverBlogSearchResult.failed(normalizedQuery, sort, display, "네이버 블로그 검색 실패 status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<NaverBlogSearchItem> items = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                items.add(new NaverBlogSearchItem(
                        cleanText(item.path("title").asText(null)),
                        cleanText(item.path("link").asText(null)),
                        cleanText(item.path("description").asText(null)),
                        cleanText(item.path("bloggername").asText(null)),
                        cleanText(item.path("bloggerlink").asText(null)),
                        cleanText(item.path("postdate").asText(null))
                ));
            }
            return new NaverBlogSearchResult(
                    normalizedQuery,
                    sort,
                    display,
                    true,
                    true,
                    items,
                    null
            );
        } catch (IOException exception) {
            return NaverBlogSearchResult.failed(normalizedQuery, sort, display, "네이버 블로그 검색 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return NaverBlogSearchResult.failed(normalizedQuery, sort, display, "네이버 블로그 검색이 중단되었습니다.");
        } catch (Exception exception) {
            return NaverBlogSearchResult.failed(normalizedQuery, sort, display, "네이버 블로그 검색 결과를 해석하지 못했습니다.");
        }
    }

    private String normalizeSort(String sort) {
        String normalized = sort == null ? "sim" : sort.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("date") ? "date" : "sim";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?is)<[^>]+>", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
