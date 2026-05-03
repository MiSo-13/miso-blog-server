package com.miso.blog.reference.dto;

import com.miso.blog.reference.code.BlogReferenceType;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record UpdateBlogReferenceUrlRequest(
        BlogReferenceType type,

        @Size(max = 200)
        String title,

        @URL
        @Size(max = 1000)
        String url,

        @Size(max = 1000)
        String description,

        List<@Size(max = 100) String> tags,

        Boolean active
) {
}
