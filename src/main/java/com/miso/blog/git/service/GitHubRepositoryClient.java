package com.miso.blog.git.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.git.dto.RepositoryCommitSnapshot;
import com.miso.blog.git.dto.RepositoryFilePatch;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GitHubRepositoryClient {
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";
    private static final int PATCH_LIMIT_PER_FILE = 5000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${blog.github.token:}")
    private String githubToken;

    public List<RepositoryCommitSnapshot> fetchRecentCommits(String repositoryFullName, String branch, int limit) {
        validateToken();
        if (limit < 1) {
            return List.of();
        }

        List<JsonNode> commitSummaries = fetchCommitSummaries(repositoryFullName, branch, limit);
        List<RepositoryCommitSnapshot> snapshots = new ArrayList<>();
        for (JsonNode commitSummary : commitSummaries) {
            String sha = commitSummary.path("sha").asText();
            snapshots.add(fetchCommitDetail(repositoryFullName, sha));
        }
        return snapshots;
    }

    public String fetchFileContent(String repositoryFullName, String branch, String filePath, int maxLength) {
        validateToken();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_BASE_URL
                            + "/repos/" + repositoryFullName
                            + "/contents/" + encodePath(filePath)
                            + "?ref=" + urlEncode(branch)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + githubToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() >= 400) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!"file".equals(root.path("type").asText()) || root.path("encoding").asText().isBlank()) {
                return null;
            }
            String encodedContent = root.path("content").asText("").replaceAll("\\s", "");
            if (encodedContent.isBlank()) {
                return null;
            }
            String content = new String(Base64.getDecoder().decode(encodedContent), StandardCharsets.UTF_8);
            return limit(content, maxLength);
        } catch (IllegalArgumentException exception) {
            return null;
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private List<JsonNode> fetchCommitSummaries(String repositoryFullName, String branch, int limit) {
        List<JsonNode> summaries = new ArrayList<>();
        int page = 1;
        while (summaries.size() < limit) {
            int perPage = Math.min(100, limit - summaries.size());
            JsonNode commits = fetchJson(
                    GITHUB_API_BASE_URL
                            + "/repos/" + repositoryFullName
                            + "/commits?sha=" + urlEncode(branch)
                            + "&per_page=" + perPage
                            + "&page=" + page
            );
            if (!commits.isArray() || commits.isEmpty()) {
                break;
            }
            for (JsonNode commitSummary : commits) {
                summaries.add(commitSummary);
            }
            if (commits.size() < perPage) {
                break;
            }
            page++;
        }
        return summaries;
    }

    private RepositoryCommitSnapshot fetchCommitDetail(String repositoryFullName, String sha) {
        JsonNode root = fetchJson(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/commits/" + sha);
        JsonNode commit = root.path("commit");
        JsonNode author = commit.path("author");

        List<RepositoryFilePatch> files = new ArrayList<>();
        for (JsonNode fileNode : root.path("files")) {
            String patch = textOrNull(fileNode, "patch");
            files.add(new RepositoryFilePatch(
                    textOrNull(fileNode, "filename"),
                    textOrNull(fileNode, "status"),
                    fileNode.path("additions").isMissingNode() ? null : fileNode.path("additions").asInt(),
                    fileNode.path("deletions").isMissingNode() ? null : fileNode.path("deletions").asInt(),
                    limitPatch(patch)
            ));
        }

        return new RepositoryCommitSnapshot(
                sha,
                textOrNull(commit, "message"),
                textOrNull(author, "name"),
                OffsetDateTime.parse(author.path("date").asText()).toLocalDateTime(),
                files
        );
    }

    private JsonNode fetchJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + githubToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new GeneralException(ErrorCode.FORBIDDEN, "GitHub 저장소 접근 권한이 없습니다. token 권한을 확인해주세요.");
            }
            if (response.statusCode() == 404) {
                throw new GeneralException(ErrorCode.NOT_FOUND, "GitHub 저장소 또는 브랜치를 찾을 수 없습니다.");
            }
            if (response.statusCode() >= 400) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub API 호출에 실패했습니다. status=" + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub API 응답을 해석하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub API 호출이 중단되었습니다.");
        }
    }

    private void validateToken() {
        if (githubToken == null || githubToken.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub token이 설정되어 있지 않습니다.");
        }
    }

    private String limitPatch(String patch) {
        if (patch == null || patch.length() <= PATCH_LIMIT_PER_FILE) {
            return patch;
        }
        return patch.substring(0, PATCH_LIMIT_PER_FILE) + "\n... patch truncated ...";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... file content truncated ...";
    }

    private String textOrNull(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String filePath) {
        String[] segments = filePath.split("/");
        List<String> encodedSegments = new ArrayList<>();
        for (String segment : segments) {
            encodedSegments.add(urlEncode(segment));
        }
        return String.join("/", encodedSegments);
    }
}
