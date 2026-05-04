package com.miso.blog.publish.service;

import com.miso.blog.publish.code.PublishChannel;
import com.miso.blog.publish.code.PublishRole;
import com.miso.blog.publish.entity.PublishTargetEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubPagesTargetDefaultsTest {

    @Test
    void baseUrlIncludesRepositoryNameForProjectPages() {
        GitHubPagesTargetDefaults defaults = new GitHubPagesTargetDefaults();
        ReflectionTestUtils.setField(defaults, "owner", "MiSo-13");
        ReflectionTestUtils.setField(defaults, "baseUrl", "");
        ReflectionTestUtils.setField(defaults, "repositoryFullName", "");
        ReflectionTestUtils.setField(defaults, "repositoryName", "");

        PublishTargetEntity target = PublishTargetEntity.builder()
                .channel(PublishChannel.GITHUB_PAGES)
                .role(PublishRole.PRIMARY)
                .name("Tech Blog")
                .repositoryFullName("MiSo-13/tech-blog")
                .active(true)
                .build();

        assertEquals("https://miso-13.github.io/tech-blog", defaults.baseUrl(target));
    }

    @Test
    void baseUrlOmitsRepositoryNameForUserPages() {
        GitHubPagesTargetDefaults defaults = new GitHubPagesTargetDefaults();
        ReflectionTestUtils.setField(defaults, "owner", "MiSo-13");
        ReflectionTestUtils.setField(defaults, "baseUrl", "");
        ReflectionTestUtils.setField(defaults, "repositoryFullName", "");
        ReflectionTestUtils.setField(defaults, "repositoryName", "");

        PublishTargetEntity target = PublishTargetEntity.builder()
                .channel(PublishChannel.GITHUB_PAGES)
                .role(PublishRole.PRIMARY)
                .name("User Pages")
                .repositoryFullName("MiSo-13/MiSo-13.github.io")
                .active(true)
                .build();

        assertEquals("https://miso-13.github.io", defaults.baseUrl(target));
    }
}
