package com.miso.blog.naver.dto;

public record NaverBlogSearchItem(
        String title,
        String link,
        String description,
        String bloggerName,
        String bloggerLink,
        String postDate
) {
}
