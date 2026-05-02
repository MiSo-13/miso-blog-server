package com.miso.blog.publish.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.publish.dto.GitHubContentCommitResult;
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
import java.util.LinkedHashMap;
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
}
