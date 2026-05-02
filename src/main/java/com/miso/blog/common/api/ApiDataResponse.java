package com.miso.blog.common.api;

public record ApiDataResponse<T>(
        boolean success,
        T data
) {
    public static <T> ApiDataResponse<T> ok(T data) {
        return new ApiDataResponse<>(true, data);
    }
}
