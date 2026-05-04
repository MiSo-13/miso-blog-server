package com.miso.blog.naver.dto;

import java.util.List;

public record NaverBlogSearchResult(
        String query,
        String sort,
        int display,
        boolean enabled,
        boolean success,
        List<NaverBlogSearchItem> items,
        String errorMessage
) {
    public static NaverBlogSearchResult disabled(String query, String reason) {
        return new NaverBlogSearchResult(
                query,
                "sim",
                0,
                false,
                false,
                List.of(),
                reason
        );
    }

    public static NaverBlogSearchResult failed(String query, String sort, int display, String errorMessage) {
        return new NaverBlogSearchResult(
                query,
                sort,
                display,
                true,
                false,
                List.of(),
                errorMessage
        );
    }
}
