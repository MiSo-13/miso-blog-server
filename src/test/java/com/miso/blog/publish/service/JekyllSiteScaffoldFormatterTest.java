package com.miso.blog.publish.service;

import com.miso.blog.publish.dto.GitHubContentFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JekyllSiteScaffoldFormatterTest {
    private final JekyllSiteScaffoldFormatter formatter = new JekyllSiteScaffoldFormatter();

    @Test
    void buildFilesCreatesJekyllProjectSiteConfig() {
        List<GitHubContentFile> files = formatter.buildFiles(
                "MiSo Tech Blog",
                "개발 기록",
                "MiSo",
                "https://miso-13.github.io/tech-blog"
        );

        String config = contentOf(files, "_config.yml");

        assertTrue(config.contains("title: \"MiSo Tech Blog\""));
        assertTrue(config.contains("url: \"https://miso-13.github.io\""));
        assertTrue(config.contains("baseurl: \"/tech-blog\""));
        assertTrue(config.contains("permalink: /:year/:month/:day/:title.html"));
    }

    @Test
    void requiredFilePathsMatchesScaffoldFiles() {
        List<String> scaffoldPaths = formatter.buildFiles(null, null, null, null)
                .stream()
                .map(GitHubContentFile::filePath)
                .toList();

        assertEquals(formatter.requiredFilePaths(), scaffoldPaths);
    }

    private String contentOf(List<GitHubContentFile> files, String filePath) {
        return files.stream()
                .filter(file -> file.filePath().equals(filePath))
                .findFirst()
                .map(GitHubContentFile::content)
                .orElseThrow();
    }
}
