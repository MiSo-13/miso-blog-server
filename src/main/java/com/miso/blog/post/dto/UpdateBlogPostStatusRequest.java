package com.miso.blog.post.dto;

import com.miso.blog.post.code.BlogPostStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateBlogPostStatusRequest(
        @NotNull
        BlogPostStatus status
) {
}
