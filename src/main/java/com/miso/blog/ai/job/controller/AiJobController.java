package com.miso.blog.ai.job.controller;

import com.miso.blog.ai.job.dto.AiJobResponse;
import com.miso.blog.ai.job.service.AiJobService;
import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.post.dto.BlogPostQualityImproveRequest;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.dto.ReviseBlogPostWithAiRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-jobs")
@Tag(name = "AI 작업", description = "시간이 오래 걸리는 AI 글 작성/수정 작업을 비동기로 실행하고 상태를 조회합니다.")
public class AiJobController {
    private final AiJobService aiJobService;

    @PostMapping("/blog-posts/draft/ai-general")
    @Operation(summary = "일반 블로그 AI 초안 생성 작업 시작")
    public ApiDataResponse<AiJobResponse> createGeneralBlogDraftJob(@Valid @RequestBody CreateGeneralBlogPostRequest request) {
        return ApiDataResponse.ok(aiJobService.createGeneralBlogDraftJob(request));
    }

    @PostMapping("/blog-posts/{blogPostId}/revise/ai")
    @Operation(summary = "AI 추가 요청 기반 글 수정 작업 시작")
    public ApiDataResponse<AiJobResponse> createBlogPostRevisionJob(
            @PathVariable Long blogPostId,
            @Valid @RequestBody ReviseBlogPostWithAiRequest request
    ) {
        return ApiDataResponse.ok(aiJobService.createBlogPostRevisionJob(blogPostId, request));
    }

    @PostMapping("/blog-posts/{blogPostId}/quality-improve/ai")
    @Operation(summary = "AI 블로그 품질 자동 개선 작업 시작")
    public ApiDataResponse<AiJobResponse> createBlogPostQualityImproveJob(
            @PathVariable Long blogPostId,
            @Valid @RequestBody(required = false) BlogPostQualityImproveRequest request
    ) {
        return ApiDataResponse.ok(aiJobService.createBlogPostQualityImproveJob(blogPostId, request));
    }

    @GetMapping
    @Operation(summary = "AI 작업 목록 조회")
    public ApiDataResponse<List<AiJobResponse>> getJobs() {
        return ApiDataResponse.ok(aiJobService.getJobs());
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "AI 작업 상태 조회")
    public ApiDataResponse<AiJobResponse> getJob(@PathVariable Long jobId) {
        return ApiDataResponse.ok(aiJobService.getJob(jobId));
    }
}
