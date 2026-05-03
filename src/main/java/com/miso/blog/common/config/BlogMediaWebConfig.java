package com.miso.blog.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class BlogMediaWebConfig implements WebMvcConfigurer {
    @Value("${blog.media.upload-dir:uploads/blog-media}")
    private String uploadDir;

    @Value("${blog.media.public-url-prefix:/media}")
    private String publicUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = normalizePublicPrefix(publicUrlPrefix) + "/**";
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(pattern)
                .addResourceLocations(location);
    }

    private String normalizePublicPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "/media";
        }
        String normalized = value.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }
}
