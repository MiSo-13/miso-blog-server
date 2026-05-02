package com.miso.blog.common.api;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        LocalDateTime occurredAt
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, code, message, LocalDateTime.now());
    }
}
