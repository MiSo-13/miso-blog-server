package com.miso.blog.post.dto;

import jakarta.validation.constraints.Size;

public record GeneralBlogPhotoRequest(
        @Size(max = 1000)
        String url,

        @Size(max = 500)
        String description,

        @Size(max = 500)
        String placementNote
) {
}
