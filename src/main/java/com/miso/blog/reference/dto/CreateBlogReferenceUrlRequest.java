package com.miso.blog.reference.dto;

import com.miso.blog.reference.code.BlogReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record CreateBlogReferenceUrlRequest(
        @NotNull
        BlogReferenceType type,

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @URL
        @Size(max = 1000)
        String url,

        @Size(max = 1000)
        String description,

        List<@Size(max = 100) String> tags,

        Boolean active
) {
}
