package com.miso.blog.localrepo.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.localrepo.dto.AnalyzeLocalRepositoryRequest;
import com.miso.blog.localrepo.dto.CloneGitHubRepositoryRequest;
import com.miso.blog.localrepo.dto.CreateLocalRepositoryRequest;
import com.miso.blog.localrepo.dto.LocalRepositoryAnalysisReportResponse;
import com.miso.blog.localrepo.dto.LocalRepositoryDefaultResponse;
import com.miso.blog.localrepo.dto.LocalRepositoryResponse;
import com.miso.blog.localrepo.dto.UpdateLocalRepositoryRequest;
import com.miso.blog.localrepo.service.GitHubRepositoryCloneService;
import com.miso.blog.localrepo.service.LocalRepositoryAnalysisService;
import com.miso.blog.post.dto.CreateBlogPostFromAnalysisRequest;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.publish.dto.GitHubBranchOptionResponse;
import com.miso.blog.publish.dto.GitHubRepositoryOptionResponse;
import com.miso.blog.publish.service.PublishTargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/local-repositories")
@Tag(name = "로컬 Git 저장소 분석", description = "로컬에 clone된 Git 저장소를 외부 전송 없이 분석합니다.")
public class LocalRepositoryController {
    private final LocalRepositoryAnalysisService localRepositoryAnalysisService;
    private final PublishTargetService publishTargetService;
    private final GitHubRepositoryCloneService gitHubRepositoryCloneService;

    @PostMapping
    @Operation(summary = "로컬 Git 저장소 등록")
    public ApiDataResponse<LocalRepositoryResponse> createRepository(@Valid @RequestBody CreateLocalRepositoryRequest request) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.createRepository(request));
    }

    @GetMapping
    @Operation(summary = "로컬 Git 저장소 목록 조회")
    public ApiDataResponse<List<LocalRepositoryResponse>> getRepositories() {
        return ApiDataResponse.ok(localRepositoryAnalysisService.getRepositories());
    }

    @GetMapping("/defaults")
    @Operation(summary = "application-private 로컬 Git 저장소 후보 조회")
    public ApiDataResponse<List<LocalRepositoryDefaultResponse>> getDefaultRepositories() {
        return ApiDataResponse.ok(localRepositoryAnalysisService.getDefaultRepositories());
    }

    @GetMapping("/github/repositories")
    @Operation(summary = "GitHub 분석 대상 저장소 목록 조회", description = "github.owner와 token으로 접근 가능한 저장소를 조회합니다.")
    public ApiDataResponse<List<GitHubRepositoryOptionResponse>> getGitHubRepositories() {
        return ApiDataResponse.ok(publishTargetService.getGitHubRepositories());
    }

    @GetMapping("/github/branches")
    @Operation(summary = "GitHub 분석 대상 브랜치 목록 조회")
    public ApiDataResponse<List<GitHubBranchOptionResponse>> getGitHubBranches(@RequestParam String repositoryFullName) {
        return ApiDataResponse.ok(publishTargetService.getGitHubBranches(repositoryFullName));
    }

    @PostMapping("/github/clone")
    @Operation(summary = "GitHub 저장소 clone 후 로컬 분석 대상으로 등록", description = "Docker 내부 clone 경로에 저장소를 내려받고 LOCAL_ONLY 분석에 사용할 수 있게 등록합니다.")
    public ApiDataResponse<LocalRepositoryResponse> cloneGitHubRepository(@Valid @RequestBody CloneGitHubRepositoryRequest request) {
        return ApiDataResponse.ok(gitHubRepositoryCloneService.cloneAndRegister(request));
    }

    @GetMapping("/{repositoryId}")
    @Operation(summary = "로컬 Git 저장소 상세 조회")
    public ApiDataResponse<LocalRepositoryResponse> getRepository(@PathVariable Long repositoryId) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.getRepository(repositoryId));
    }

    @PatchMapping("/{repositoryId}")
    @Operation(summary = "로컬 Git 저장소 수정")
    public ApiDataResponse<LocalRepositoryResponse> updateRepository(
            @PathVariable Long repositoryId,
            @Valid @RequestBody UpdateLocalRepositoryRequest request
    ) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.updateRepository(repositoryId, request));
    }

    @PostMapping("/{repositoryId}/analyze")
    @Operation(summary = "로컬 Git 저장소 분석", description = "기본값은 LOCAL_ONLY이며 외부 AI로 코드를 전송하지 않습니다.")
    public ApiDataResponse<LocalRepositoryAnalysisReportResponse> analyze(
            @PathVariable Long repositoryId,
            @Valid @RequestBody AnalyzeLocalRepositoryRequest request
    ) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.analyze(repositoryId, request));
    }

    @GetMapping("/{repositoryId}/analysis-reports")
    @Operation(summary = "로컬 Git 분석 결과 목록 조회")
    public ApiDataResponse<List<LocalRepositoryAnalysisReportResponse>> getReports(@PathVariable Long repositoryId) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.getReports(repositoryId));
    }

    @GetMapping("/analysis-reports/{reportId}")
    @Operation(summary = "로컬 Git 분석 결과 상세 조회")
    public ApiDataResponse<LocalRepositoryAnalysisReportResponse> getReport(@PathVariable Long reportId) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.getReport(reportId));
    }

    @DeleteMapping("/{repositoryId}/analysis-reports")
    @Operation(summary = "로컬 Git 분석 결과 전체 삭제", description = "선택한 로컬 저장소의 기존 분석 결과를 모두 삭제하고 다시 분석할 수 있게 합니다.")
    public ApiDataResponse<Void> deleteReports(@PathVariable Long repositoryId) {
        localRepositoryAnalysisService.deleteReports(repositoryId);
        return ApiDataResponse.ok(null);
    }

    @DeleteMapping("/analysis-reports/{reportId}")
    @Operation(summary = "로컬 Git 분석 결과 삭제", description = "선택한 로컬 분석 결과 1건을 삭제합니다.")
    public ApiDataResponse<Void> deleteReport(@PathVariable Long reportId) {
        localRepositoryAnalysisService.deleteReport(reportId);
        return ApiDataResponse.ok(null);
    }

    @PostMapping("/analysis-reports/{reportId}/blog-post")
    @Operation(summary = "로컬 Git 분석 결과를 블로그 초안으로 전환")
    public ApiDataResponse<BlogPostResponse> createBlogPostFromReport(@PathVariable Long reportId) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.createBlogPostFromReport(reportId));
    }

    @PostMapping("/analysis-reports/{reportId}/write-blog-post")
    @Operation(summary = "선택 키워드 기반 블로그 글 작성", description = "분석 결과에서 키워드와 주제를 선택해 더 좋은 블로그 초안을 생성합니다.")
    public ApiDataResponse<BlogPostResponse> writeBlogPostFromReport(
            @PathVariable Long reportId,
            @Valid @RequestBody CreateBlogPostFromAnalysisRequest request
    ) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.writeBlogPostFromReport(reportId, request));
    }
}
