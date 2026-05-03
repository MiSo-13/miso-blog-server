package com.miso.blog.publish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.publish.dto.GitHubBranchOptionResponse;
import com.miso.blog.publish.dto.GitHubContentCommitResult;
import com.miso.blog.publish.dto.GitHubRepositoryOptionResponse;
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
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class GitHubContentsClient {
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${blog.github.token:}")
    private String githubToken;

    public GitHubContentCommitResult putFile(
            String repositoryFullName,
            String branchName,
            String filePath,
            String markdown,
            String commitMessage
    ) {
        validateToken();
        validateRepository(repositoryFullName);

        try {
            String currentSha = fetchCurrentSha(repositoryFullName, branchName, filePath);
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("message", commitMessage);
            requestBody.put("content", Base64.getEncoder().encodeToString(markdown.getBytes(StandardCharsets.UTF_8)));
            requestBody.put("branch", branchName);
            if (currentSha != null) {
                requestBody.put("sha", currentSha);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(contentsUri(repositoryFullName, filePath, null))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Content-Type", "application/json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 파일 발행에 실패했습니다. status=" + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return new GitHubContentCommitResult(
                    root.path("content").path("path").asText(filePath),
                    root.path("commit").path("sha").asText(null),
                    root.path("commit").path("html_url").asText(null),
                    root.path("content").path("html_url").asText(null)
            );
        } catch (GeneralException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행이 중단되었습니다.");
        }
    }

    public GitHubConnectionCheckResult checkConnection(
            String repositoryFullName,
            String branchName,
            String contentRootPath
    ) {
        validateToken();
        validateRepository(repositoryFullName);
        if (branchName == null || branchName.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 브랜치명이 설정되어 있지 않습니다.");
        }

        try {
            List<String> checkedItems = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            JsonNode repository = getJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName));
            checkedItems.add("repository");

            JsonNode branch = getJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/branches/" + encode(branchName)));
            checkedItems.add("branch");

            String normalizedRootPath = normalizeContentRootPath(contentRootPath);
            JsonNode contentRoot = null;
            if (normalizedRootPath != null) {
                try {
                    contentRoot = getJson(contentsUri(repositoryFullName, normalizedRootPath, branchName));
                    checkedItems.add("contentRootPath");
                } catch (GeneralException exception) {
                    warnings.add("contentRootPath를 찾지 못했습니다. 발행 시 GitHub Contents API가 새 파일 경로를 만들 수 있지만, 상위 폴더 구조는 확인해 주세요.");
                }
            }

            return new GitHubConnectionCheckResult(
                    repository.path("html_url").asText(null),
                    branch.path("_links").path("html").asText(null),
                    contentRoot == null ? null : contentRoot.path("html_url").asText(null),
                    checkedItems,
                    warnings
            );
        } catch (GeneralException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 연결 확인 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 연결 확인이 중단되었습니다.");
        }
    }

    public List<GitHubRepositoryOptionResponse> listRepositories(String ownerLogin) {
        validateToken();
        String normalizedOwner = trimToNull(ownerLogin);
        if (normalizedOwner == null) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "github.owner를 먼저 설정하세요.");
        }

        try {
            List<GitHubRepositoryOptionResponse> repositories = new ArrayList<>();
            for (int page = 1; page <= 5; page++) {
                URI uri = URI.create(GITHUB_API_BASE_URL
                        + "/user/repos?per_page=100&page=" + page
                        + "&affiliation=owner,collaborator,organization_member&sort=updated");
                JsonNode root = getJson(uri);
                if (!root.isArray() || root.isEmpty()) {
                    break;
                }
                for (JsonNode repository : root) {
                    String repositoryOwner = repository.path("owner").path("login").asText("");
                    if (!repositoryOwner.equalsIgnoreCase(normalizedOwner)) {
                        continue;
                    }
                    String name = repository.path("name").asText("");
                    String fullName = repository.path("full_name").asText("");
                    repositories.add(new GitHubRepositoryOptionResponse(
                            name,
                            fullName,
                            repositoryOwner,
                            repository.path("default_branch").asText("main"),
                            repository.path("private").asBoolean(false),
                            repository.path("fork").asBoolean(false),
                            isGitHubPagesCandidate(normalizedOwner, name),
                            repository.path("html_url").asText(null),
                            repository.path("updated_at").asText(null)
                    ));
                }
                if (root.size() < 100) {
                    break;
                }
            }
            return repositories;
        } catch (GeneralException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 목록 조회 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 저장소 목록 조회가 중단되었습니다.");
        }
    }

    public List<GitHubBranchOptionResponse> listBranches(String repositoryFullName) {
        validateToken();
        validateRepository(repositoryFullName);

        try {
            List<GitHubBranchOptionResponse> branches = new ArrayList<>();
            for (int page = 1; page <= 5; page++) {
                URI uri = URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName
                        + "/branches?per_page=100&page=" + page);
                JsonNode root = getJson(uri);
                if (!root.isArray() || root.isEmpty()) {
                    break;
                }
                for (JsonNode branch : root) {
                    branches.add(new GitHubBranchOptionResponse(
                            branch.path("name").asText(""),
                            branch.path("commit").path("sha").asText(null),
                            branch.path("protected").asBoolean(false)
                    ));
                }
                if (root.size() < 100) {
                    break;
                }
            }
            return branches;
        } catch (GeneralException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 브랜치 목록 조회 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 브랜치 목록 조회가 중단되었습니다.");
        }
    }

    private String fetchCurrentSha(String repositoryFullName, String branchName, String filePath) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(contentsUri(repositoryFullName, filePath, branchName))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + githubToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 기존 파일 확인에 실패했습니다. status=" + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root.path("sha").asText(null);
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + githubToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub API 연결 확인에 실패했습니다. status=" + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private URI contentsUri(String repositoryFullName, String filePath, String branchName) {
        String url = GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/contents/" + encodePath(filePath);
        if (branchName != null && !branchName.isBlank()) {
            url += "?ref=" + encode(branchName);
        }
        return URI.create(url);
    }

    private String encodePath(String filePath) {
        return java.util.Arrays.stream(filePath.split("/"))
                .map(this::encode)
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void validateToken() {
        if (githubToken == null || githubToken.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub token이 설정되어 있지 않습니다.");
        }
    }

    private void validateRepository(String repositoryFullName) {
        if (repositoryFullName == null || repositoryFullName.isBlank() || !repositoryFullName.contains("/")) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 저장소 full name이 올바르지 않습니다.");
        }
    }

    private boolean isGitHubPagesCandidate(String ownerLogin, String repositoryName) {
        String expectedName = ownerLogin.toLowerCase(Locale.ROOT) + ".github.io";
        return repositoryName.equalsIgnoreCase(expectedName) || repositoryName.toLowerCase(Locale.ROOT).endsWith(".github.io");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeContentRootPath(String contentRootPath) {
        if (contentRootPath == null || contentRootPath.isBlank()) {
            return null;
        }
        String normalized = contentRootPath.trim()
                .replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    public record GitHubConnectionCheckResult(
            String repositoryUrl,
            String branchUrl,
            String contentRootUrl,
            List<String> checkedItems,
            List<String> warnings
    ) {
    }
}
