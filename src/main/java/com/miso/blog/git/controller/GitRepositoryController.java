package com.miso.blog.git.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.git.dto.AnalyzeGitRepositoryRequest;
import com.miso.blog.git.dto.CreateGitRepositoryRequest;
import com.miso.blog.git.dto.GitAnalysisReportResponse;
import com.miso.blog.git.dto.GitRepositoryResponse;
import com.miso.blog.git.dto.GitRepositoryUpdateRequest;
import com.miso.blog.git.service.GitRepositoryAnalysisService;
import com.miso.blog.post.dto.CreateBlogPostFromAnalysisRequest;
import com.miso.blog.post.dto.BlogPostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/git-repositories")
@Tag(name = "Git 저장소 분석", description = "private GitHub 저장소를 읽고 AI로 블로그 글감과 초안을 추출합니다.")
public class GitRepositoryController {
    private final GitRepositoryAnalysisService gitRepositoryAnalysisService;

    @PostMapping
    @Operation(summary = "Git 저장소 등록", description = "owner/repo 형식의 GitHub 저장소를 등록합니다.")
    public ApiDataResponse<GitRepositoryResponse> createRepository(@Valid @RequestBody CreateGitRepositoryRequest request) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.createRepository(request));
    }

    @GetMapping
    @Operation(summary = "Git 저장소 목록 조회")
    public ApiDataResponse<List<GitRepositoryResponse>> getRepositories() {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.getRepositories());
    }

    @GetMapping("/{repositoryId}")
    @Operation(summary = "Git 저장소 상세 조회")
    public ApiDataResponse<GitRepositoryResponse> getRepository(@PathVariable Long repositoryId) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.getRepository(repositoryId));
    }

    @PatchMapping("/{repositoryId}")
    @Operation(summary = "Git 저장소 수정")
    public ApiDataResponse<GitRepositoryResponse> updateRepository(
            @PathVariable Long repositoryId,
            @Valid @RequestBody GitRepositoryUpdateRequest request
    ) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.updateRepository(repositoryId, request));
    }

    @PostMapping("/{repositoryId}/analyze")
    @Operation(summary = "Git 저장소 AI 분석", description = "최근 commit patch를 OpenAI로 분석해 키워드, 글감 후보, Markdown 초안을 생성합니다.")
    public ApiDataResponse<GitAnalysisReportResponse> analyze(
            @PathVariable Long repositoryId,
            @Valid @RequestBody AnalyzeGitRepositoryRequest request
    ) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.analyze(repositoryId, request));
    }

    @GetMapping("/{repositoryId}/analysis-reports")
    @Operation(summary = "Git 저장소 분석 결과 목록 조회")
    public ApiDataResponse<List<GitAnalysisReportResponse>> getReports(@PathVariable Long repositoryId) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.getReports(repositoryId));
    }

    @GetMapping("/analysis-reports/{reportId}")
    @Operation(summary = "Git 분석 결과 상세 조회")
    public ApiDataResponse<GitAnalysisReportResponse> getReport(@PathVariable Long reportId) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.getReport(reportId));
    }

    @PostMapping("/analysis-reports/{reportId}/blog-post")
    @Operation(summary = "Git 분석 결과를 블로그 초안으로 전환")
    public ApiDataResponse<BlogPostResponse> createBlogPostFromReport(@PathVariable Long reportId) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.createBlogPostFromReport(reportId));
    }

    @PostMapping("/analysis-reports/{reportId}/write-blog-post")
    @Operation(summary = "선택 키워드 기반 블로그 글 작성", description = "분석 결과에서 키워드와 주제를 선택해 더 좋은 블로그 초안을 생성합니다.")
    public ApiDataResponse<BlogPostResponse> writeBlogPostFromReport(
            @PathVariable Long reportId,
            @Valid @RequestBody CreateBlogPostFromAnalysisRequest request
    ) {
        return ApiDataResponse.ok(gitRepositoryAnalysisService.writeBlogPostFromReport(reportId, request));
    }
}
