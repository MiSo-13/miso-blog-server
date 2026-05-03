package com.miso.blog.post.dto;

import com.miso.blog.post.code.GeneralBlogCategory;
import com.miso.blog.post.code.GeneralBlogLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGeneralBlogPostRequest(
        @NotNull
        GeneralBlogCategory category,

        @Size(max = 200)
        String titleHint,

        @Size(max = 200)
        String placeName,

        @Size(max = 200)
        String addressHint,

        List<@Size(max = 200) String> requiredPhrases,

        @Size(max = 6000)
        String memo,

        List<@Size(max = 100) String> keywords,

        List<@Valid GeneralBlogPhotoRequest> photos,

        @Size(max = 300)
        String imagePlacementNotes,

        @Size(max = 200)
        String tone,

        @Size(max = 200)
        String audience,

        GeneralBlogLength targetLength,

        Boolean markReviewReady
) {
}
