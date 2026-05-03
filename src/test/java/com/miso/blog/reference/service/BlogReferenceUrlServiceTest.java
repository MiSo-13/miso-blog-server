package com.miso.blog.reference.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.dto.UpdateBlogReferenceUrlRequest;
import com.miso.blog.reference.entity.BlogReferenceUrlEntity;
import com.miso.blog.reference.repository.BlogReferenceUrlRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlogReferenceUrlServiceTest {
    @Test
    void updateReferenceUrlPreservesOmittedFields() {
        BlogReferenceUrlRepository repository = mock(BlogReferenceUrlRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        BlogReferenceUrlEntity referenceUrl = BlogReferenceUrlEntity.builder()
                .type(BlogReferenceType.GENERAL)
                .title("Map page")
                .url("https://map.example.com/place")
                .description("Original memo")
                .tagsJson("[\"food\"]")
                .active(true)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(referenceUrl));

        BlogReferenceUrlService service = new BlogReferenceUrlService(repository, objectMapper);
        service.updateReferenceUrl(1L, new UpdateBlogReferenceUrlRequest(
                null,
                null,
                null,
                "Updated memo",
                null,
                false
        ));

        assertEquals(BlogReferenceType.GENERAL, referenceUrl.getType());
        assertEquals("Map page", referenceUrl.getTitle());
        assertEquals("https://map.example.com/place", referenceUrl.getUrl());
        assertEquals("Updated memo", referenceUrl.getDescription());
        assertEquals("[\"food\"]", referenceUrl.getTagsJson());
        assertFalse(referenceUrl.isActive());
    }

    @Test
    void updateReferenceUrlCanClearTags() {
        BlogReferenceUrlRepository repository = mock(BlogReferenceUrlRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        BlogReferenceUrlEntity referenceUrl = BlogReferenceUrlEntity.builder()
                .type(BlogReferenceType.DEVELOPMENT)
                .title("Docs")
                .url("https://docs.example.com")
                .description("Original memo")
                .tagsJson("[\"spring\"]")
                .active(true)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(referenceUrl));

        BlogReferenceUrlService service = new BlogReferenceUrlService(repository, objectMapper);
        service.updateReferenceUrl(1L, new UpdateBlogReferenceUrlRequest(
                null,
                null,
                null,
                null,
                List.of(),
                null
        ));

        assertEquals("[]", referenceUrl.getTagsJson());
    }
}
