package com.miso.blog.publish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.publish.dto.GitHubBranchOptionResponse;
import com.miso.blog.publish.dto.GitHubContentCommitResult;
import com.miso.blog.publish.dto.GitHubContentFile;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            String content,
            String commitMessage
    ) {
        return putFiles(
                repositoryFullName,
                branchName,
                List.of(new GitHubContentFile(filePath, content)),
                commitMessage
        ).get(0);
    }

    public List<GitHubContentCommitResult> putFiles(
            String repositoryFullName,
            String branchName,
            List<GitHubContentFile> files,
            String commitMessage
    ) {
        validateToken();
        validateRepository(repositoryFullName);
        validateBranch(branchName);
        if (files == null || files.isEmpty()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages에 commit할 파일이 없습니다.");
        }

        try {
            List<GitHubContentFile> normalizedFiles = normalizeFiles(files);
            BranchResolution branch = resolveBranch(repositoryFullName, branchName);
            if (branch.emptyRepository()) {
                return putFilesIntoEmptyRepository(repositoryFullName, branchName, normalizedFiles, commitMessage);
            }
            String treeSha = createTree(repositoryFullName, branch.baseTreeSha(), normalizedFiles);
            String commitSha = createCommit(repositoryFullName, commitMessage, treeSha, branch.parentCommitSha());
            updateBranchRef(repositoryFullName, branchName, commitSha, branch.targetBranchExists());

            String commitUrl = "https://github.com/" + repositoryFullName + "/commit/" + commitSha;
            return normalizedFiles.stream()
                    .map(file -> new GitHubContentCommitResult(
                            file.filePath(),
                            commitSha,
                            commitUrl,
                            "https://github.com/" + repositoryFullName + "/blob/" + branchName + "/" + file.filePath()
                    ))
                    .toList();
        } catch (GeneralException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 발행이 중단되었습니다.");
        }
    }

    private List<GitHubContentCommitResult> putFilesIntoEmptyRepository(
            String repositoryFullName,
            String branchName,
            List<GitHubContentFile> files,
            String commitMessage
    ) throws IOException, InterruptedException {
        GitHubContentFile firstFile = files.get(0);
        GitHubContentCommitResult firstResult = putInitialFileWithContentsApi(
                repositoryFullName,
                branchName,
                firstFile,
                commitMessage
        );
        if (files.size() == 1) {
            return List.of(firstResult);
        }

        List<GitHubContentCommitResult> results = new ArrayList<>();
        results.add(firstResult);
        results.addAll(putFiles(repositoryFullName, branchName, files.subList(1, files.size()), commitMessage));
        return results;
    }

    private GitHubContentCommitResult putInitialFileWithContentsApi(
            String repositoryFullName,
            String branchName,
            GitHubContentFile file,
            String commitMessage
    ) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("message", defaultText(commitMessage, "Initialize GitHub Pages content"));
        requestBody.put("content", Base64.getEncoder().encodeToString((file.content() == null ? "" : file.content()).getBytes(StandardCharsets.UTF_8)));
        requestBody.put("branch", branchName);

        HttpRequest request = authorizedRequestBuilder(contentsUri(repositoryFullName, file.filePath(), null))
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 빈 저장소 첫 파일 생성에 실패했습니다. status=" + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String commitSha = root.path("commit").path("sha").asText(null);
        return new GitHubContentCommitResult(
                root.path("content").path("path").asText(file.filePath()),
                commitSha,
                root.path("commit").path("html_url").asText("https://github.com/" + repositoryFullName + "/commit/" + commitSha),
                root.path("content").path("html_url").asText("https://github.com/" + repositoryFullName + "/blob/" + branchName + "/" + file.filePath())
        );
    }

    public boolean contentExists(String repositoryFullName, String branchName, String filePath) {
        validateToken();
        validateRepository(repositoryFullName);
        validateBranch(branchName);

        try {
            HttpRequest request = authorizedRequestBuilder(contentsUri(repositoryFullName, normalizeFilePath(filePath), branchName))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 409) {
                return false;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 파일 확인에 실패했습니다. status=" + response.statusCode());
            }
            return true;
        } catch (GeneralException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 파일 확인 중 네트워크 오류가 발생했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 파일 확인이 중단되었습니다.");
        }
    }

    public GitHubConnectionCheckResult checkConnection(
            String repositoryFullName,
            String branchName,
            String contentRootPath
    ) {
        validateToken();
        validateRepository(repositoryFullName);
        validateBranch(branchName);

        try {
            List<String> checkedItems = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            JsonNode repository = getJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName));
            checkedItems.add("repository");

            JsonNode branch = getJsonOrNullOn404(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/branches/" + encodePath(branchName)));
            if (branch == null) {
                warnings.add("branchName 브랜치를 찾지 못했습니다. 빈 저장소라면 Jekyll 초기화 API가 첫 commit과 브랜치를 생성할 수 있습니다.");
            } else {
                checkedItems.add("branch");
            }

            String normalizedRootPath = normalizeContentRootPath(contentRootPath);
            JsonNode contentRoot = null;
            if (branch != null && normalizedRootPath != null) {
                contentRoot = getJsonOrNullOn404(contentsUri(repositoryFullName, normalizedRootPath, branchName));
                if (contentRoot == null) {
                    warnings.add("contentRootPath를 찾지 못했습니다. 아직 발행된 글이 없다면 _posts 폴더가 Git에 없을 수 있습니다.");
                } else {
                    checkedItems.add("contentRootPath");
                }
            }

            return new GitHubConnectionCheckResult(
                    repository.path("html_url").asText(null),
                    branch == null ? null : branch.path("_links").path("html").asText(null),
                    contentRoot == null ? null : contentRoot.path("html_url").asText(null),
                    checkedItems,
                    warnings,
                    branch != null
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
                JsonNode root = getJsonOrNullOn404(uri);
                if (root == null || !root.isArray() || root.isEmpty()) {
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

    private BranchResolution resolveBranch(String repositoryFullName, String branchName) throws IOException, InterruptedException {
        String branchCommitSha = fetchBranchCommitSha(repositoryFullName, branchName);
        if (branchCommitSha != null) {
            return new BranchResolution(
                    branchCommitSha,
                    fetchCommitTreeSha(repositoryFullName, branchCommitSha),
                    true
            );
        }

        String defaultBranch = fetchRepositoryDefaultBranch(repositoryFullName);
        if (defaultBranch != null && !defaultBranch.equals(branchName)) {
            String defaultBranchCommitSha = fetchBranchCommitSha(repositoryFullName, defaultBranch);
            if (defaultBranchCommitSha != null) {
                return new BranchResolution(
                        defaultBranchCommitSha,
                        fetchCommitTreeSha(repositoryFullName, defaultBranchCommitSha),
                        false
                );
            }
        }

        return new BranchResolution(null, null, false);
    }

    private String fetchRepositoryDefaultBranch(String repositoryFullName) throws IOException, InterruptedException {
        JsonNode repository = getJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName));
        return trimToNull(repository.path("default_branch").asText(null));
    }

    private String fetchBranchCommitSha(String repositoryFullName, String branchName) throws IOException, InterruptedException {
        JsonNode ref = getJsonOrNullOn404(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/git/ref/heads/" + encodePath(branchName)));
        return ref == null ? null : ref.path("object").path("sha").asText(null);
    }

    private String fetchCommitTreeSha(String repositoryFullName, String commitSha) throws IOException, InterruptedException {
        JsonNode commit = getJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/git/commits/" + commitSha));
        return commit.path("tree").path("sha").asText(null);
    }

    private String createTree(String repositoryFullName, String baseTreeSha, List<GitHubContentFile> files) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        if (baseTreeSha != null) {
            requestBody.put("base_tree", baseTreeSha);
        }
        requestBody.put("tree", files.stream()
                .map(file -> Map.of(
                        "path", file.filePath(),
                        "mode", "100644",
                        "type", "blob",
                        "content", file.content() == null ? "" : file.content()
                ))
                .toList());

        JsonNode tree = postJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/git/trees"), requestBody);
        return tree.path("sha").asText(null);
    }

    private String createCommit(String repositoryFullName, String commitMessage, String treeSha, String parentCommitSha) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("message", defaultText(commitMessage, "Update GitHub Pages content"));
        requestBody.put("tree", treeSha);
        requestBody.put("parents", parentCommitSha == null ? List.of() : List.of(parentCommitSha));

        JsonNode commit = postJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/git/commits"), requestBody);
        return commit.path("sha").asText(null);
    }

    private void updateBranchRef(String repositoryFullName, String branchName, String commitSha, boolean branchExists) throws IOException, InterruptedException {
        if (branchExists) {
            Map<String, Object> requestBody = Map.of("sha", commitSha, "force", false);
            patchJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/git/refs/heads/" + encodePath(branchName)), requestBody);
            return;
        }

        Map<String, Object> requestBody = Map.of(
                "ref", "refs/heads/" + branchName,
                "sha", commitSha
        );
        postJson(URI.create(GITHUB_API_BASE_URL + "/repos/" + repositoryFullName + "/git/refs"), requestBody);
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = authorizedRequestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub API 연결 확인에 실패했습니다. status=" + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode getJsonOrNullOn404(URI uri) throws IOException, InterruptedException {
        HttpRequest request = authorizedRequestBuilder(uri)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.statusCode() == 409) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub API 연결 확인에 실패했습니다. status=" + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode postJson(URI uri, Map<String, Object> requestBody) throws IOException, InterruptedException {
        HttpRequest request = authorizedRequestBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 파일 commit에 실패했습니다. status=" + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode patchJson(URI uri, Map<String, Object> requestBody) throws IOException, InterruptedException {
        HttpRequest request = authorizedRequestBuilder(uri)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 브랜치 ref 업데이트에 실패했습니다. status=" + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private HttpRequest.Builder authorizedRequestBuilder(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + githubToken)
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", "2022-11-28");
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

    private List<GitHubContentFile> normalizeFiles(List<GitHubContentFile> files) {
        return files.stream()
                .map(file -> new GitHubContentFile(normalizeFilePath(file.filePath()), file.content()))
                .toList();
    }

    private String normalizeFilePath(String filePath) {
        String normalized = trimToNull(filePath);
        if (normalized == null) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 파일 경로가 비어 있습니다.");
        }
        normalized = normalized.replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (normalized.isBlank() || normalized.contains("..")) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub 파일 경로가 올바르지 않습니다.");
        }
        return normalized;
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

    private void validateBranch(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            throw new GeneralException(ErrorCode.BAD_REQUEST, "GitHub Pages 브랜치명이 설정되어 있지 않습니다.");
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

    private String defaultText(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
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

    private record BranchResolution(
            String parentCommitSha,
            String baseTreeSha,
            boolean targetBranchExists
    ) {
        boolean emptyRepository() {
            return parentCommitSha == null && baseTreeSha == null && !targetBranchExists;
        }
    }

    public record GitHubConnectionCheckResult(
            String repositoryUrl,
            String branchUrl,
            String contentRootUrl,
            List<String> checkedItems,
            List<String> warnings,
            boolean branchExists
    ) {
    }
}
