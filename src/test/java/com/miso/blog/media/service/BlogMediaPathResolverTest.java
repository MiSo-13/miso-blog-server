package com.miso.blog.media.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogMediaPathResolverTest {
    private final BlogMediaPathResolver resolver = new BlogMediaPathResolver();

    @Test
    void dateDirectoryUsesYearMonthDayPath() {
        assertEquals("2026/05/03", resolver.dateDirectory(LocalDate.of(2026, 5, 3)));
    }

    @Test
    void sanitizeFilenameRemovesPathAndUnsafeCharacters() {
        assertEquals("my-image-.png", resolver.sanitizeFilename("../my image!.png"));
    }

    @Test
    void storedFilenameKeepsSafeExtension() {
        String storedFilename = resolver.storedFilename("photo.webp");

        assertTrue(storedFilename.endsWith(".webp"));
    }

    @Test
    void normalizePublicPrefixAddsLeadingSlash() {
        assertEquals("/media", resolver.normalizePublicPrefix("media/"));
    }
}
