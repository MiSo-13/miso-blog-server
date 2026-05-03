package com.miso.blog.media.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Component
public class BlogMediaPathResolver {
    private static final DateTimeFormatter DIRECTORY_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public String dateDirectory(LocalDate date) {
        return DIRECTORY_FORMATTER.format(date);
    }

    public String storedFilename(String originalFilename) {
        String extension = extension(originalFilename);
        return UUID.randomUUID() + extension;
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image";
        }
        String normalized = Normalizer.normalize(filename.trim(), Normalizer.Form.NFKC)
                .replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        basename = basename.replaceAll("[\\r\\n\\t]", "")
                .replaceAll("[^a-zA-Z0-9가-힣._-]", "-")
                .replaceAll("-+", "-");
        return basename.isBlank() ? "image" : basename;
    }

    public String normalizePublicPrefix(String publicUrlPrefix) {
        if (publicUrlPrefix == null || publicUrlPrefix.isBlank()) {
            return "/media";
        }
        String normalized = publicUrlPrefix.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }

    private String extension(String originalFilename) {
        String sanitized = sanitizeFilename(originalFilename).toLowerCase(Locale.ROOT);
        int dotIndex = sanitized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == sanitized.length() - 1) {
            return "";
        }
        String extension = sanitized.substring(dotIndex);
        return extension.length() > 15 ? "" : extension;
    }
}
