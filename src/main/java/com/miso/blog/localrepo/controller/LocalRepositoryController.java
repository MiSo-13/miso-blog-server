package com.miso.blog.localrepo.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.localrepo.dto.AnalyzeLocalRepositoryRequest;
import com.miso.blog.localrepo.dto.CreateLocalRepositoryRequest;
import com.miso.blog.localrepo.dto.LocalRepositoryAnalysisReportResponse;
import com.miso.blog.localrepo.dto.LocalRepositoryResponse;
import com.miso.blog.localrepo.dto.UpdateLocalRepositoryRequest;
import com.miso.blog.localrepo.service.LocalRepositoryAnalysisService;
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
@RequestMapping("/api/local-repositories")
@Tag(name = "로컬 Git 저장소 분석", description = "로컬에 clone된 Git 저장소를 외부 전송 없이 분석합니다.")
public class LocalRepositoryController {
    private final LocalRepositoryAnalysisService localRepositoryAnalysisService;

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

    @PostMapping("/analysis-reports/{reportId}/blog-post")
    @Operation(summary = "로컬 Git 분석 결과를 블로그 초안으로 전환")
    public ApiDataResponse<BlogPostResponse> createBlogPostFromReport(@PathVariable Long reportId) {
        return ApiDataResponse.ok(localRepositoryAnalysisService.createBlogPostFromReport(reportId));
    }
}
