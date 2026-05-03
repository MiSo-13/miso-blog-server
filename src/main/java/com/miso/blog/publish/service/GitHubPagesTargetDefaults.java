package com.miso.blog.publish.service;

import com.miso.blog.publish.entity.PublishTargetEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitHubPagesTargetDefaults {
    @Value("${blog.github.owner:}")
    private String owner;

    @Value("${blog.github.pages-repository-name:}")
    private String repositoryName;

    @Value("${blog.github.pages-repository-full-name:}")
    private String repositoryFullName;

    @Value("${blog.github.pages-branch:main}")
    private String branchName;

    @Value("${blog.github.pages-content-root-path:_posts}")
    private String contentRootPath;

    @Value("${blog.github.pages-base-url:}")
    private String baseUrl;

    @Value("${blog.github.pages-custom-domain:}")
    private String customDomain;

    public String repositoryFullName(PublishTargetEntity target) {
        String targetValue = trimToNull(target.getRepositoryFullName());
        if (targetValue != null) {
            return targetValue;
        }

        String configuredFullName = trimToNull(repositoryFullName);
        if (configuredFullName != null) {
            return configuredFullName;
        }

        String configuredOwner = trimToNull(owner);
        if (configuredOwner == null) {
            return null;
        }

        String configuredRepositoryName = trimToNull(repositoryName);
        if (configuredRepositoryName == null) {
            configuredRepositoryName = configuredOwner + ".github.io";
        }
        return configuredOwner + "/" + configuredRepositoryName;
    }

    public String branchName(PublishTargetEntity target) {
        String targetValue = trimToNull(target.getBranchName());
        return targetValue == null ? defaultText(branchName, "main") : targetValue;
    }

    public String contentRootPath(PublishTargetEntity target) {
        String targetValue = trimToNull(target.getContentRootPath());
        return targetValue == null ? defaultText(contentRootPath, "_posts") : targetValue;
    }

    public String baseUrl(PublishTargetEntity target) {
        String targetValue = trimToNull(target.getBaseUrl());
        if (targetValue != null) {
            return targetValue;
        }

        String configuredBaseUrl = trimToNull(baseUrl);
        if (configuredBaseUrl != null) {
            return configuredBaseUrl;
        }

        String configuredOwner = trimToNull(owner);
        return configuredOwner == null ? null : "https://" + configuredOwner + ".github.io";
    }

    public String customDomain(PublishTargetEntity target) {
        String targetValue = trimToNull(target.getCustomDomain());
        return targetValue == null ? trimToNull(customDomain) : targetValue;
    }

    public String resolvedPublicBaseUrl(PublishTargetEntity target) {
        String resolvedCustomDomain = customDomain(target);
        if (resolvedCustomDomain != null) {
            return resolvedCustomDomain.startsWith("http")
                    ? resolvedCustomDomain
                    : "https://" + resolvedCustomDomain;
        }
        return baseUrl(target);
    }

    private String defaultText(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
