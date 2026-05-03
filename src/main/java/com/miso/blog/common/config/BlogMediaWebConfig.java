package com.miso.blog.common.config;

import com.miso.blog.common.logging.RequestLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class BlogMediaWebConfig implements WebMvcConfigurer {
    private final RequestLoggingInterceptor requestLoggingInterceptor;

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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(normalizePublicPrefix(publicUrlPrefix) + "/**");
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
