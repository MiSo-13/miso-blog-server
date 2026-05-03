package com.miso.blog.post.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.post.dto.BlogPostQualityImproveRequest;
import com.miso.blog.post.dto.BlogPostQualityImproveResponse;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.BlogPostSummaryResponse;
import com.miso.blog.post.dto.BlogPostVersionDiffResponse;
import com.miso.blog.post.dto.BlogPostVersionResponse;
import com.miso.blog.post.dto.CreateGeneralBlogPostRequest;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.dto.BlogPostQualityReviewRequest;
import com.miso.blog.post.dto.BlogPostQualityReviewResponse;
import com.miso.blog.post.dto.UpdateBlogPostRequest;
import com.miso.blog.post.dto.UpdateBlogPostStatusRequest;
import com.miso.blog.post.dto.ReviseBlogPostWithAiRequest;
import com.miso.blog.post.service.BlogPostService;
import com.miso.blog.post.service.GeneralBlogPostService;
import com.miso.blog.post.service.BlogPostQualityImproveService;
import com.miso.blog.post.service.BlogPostRevisionService;
import com.miso.blog.post.service.BlogPostQualityReviewService;
import com.miso.blog.post.service.BlogPostVersionDiffService;
import com.miso.blog.publish.dto.ExportVelogMarkdownRequest;
import com.miso.blog.publish.dto.ExportVelogMarkdownResponse;
import com.miso.blog.publish.dto.PublishGithubPagesRequest;
import com.miso.blog.publish.dto.PublishGithubPagesResponse;
import com.miso.blog.publish.service.BlogPostPublishService;
import com.miso.blog.publish.service.VelogExportService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/blog-posts")
@Tag(name = "블로그 글", description = "AI 또는 수동으로 생성한 Markdown 블로그 글을 관리합니다.")
public class BlogPostController {
    private final BlogPostService blogPostService;
    private final GeneralBlogPostService generalBlogPostService;
    private final BlogPostRevisionService blogPostRevisionService;
    private final BlogPostQualityReviewService blogPostQualityReviewService;
    private final BlogPostQualityImproveService blogPostQualityImproveService;
    private final BlogPostVersionDiffService blogPostVersionDiffService;
    private final BlogPostPublishService blogPostPublishService;
    private final VelogExportService velogExportService;

    @PostMapping("/draft/manual")
    @Operation(summary = "수동 블로그 초안 생성", description = "Git/AI 연동 전에도 Markdown 초안을 저장할 수 있습니다.")
    public ApiDataResponse<BlogPostResponse> createManualDraft(@Valid @RequestBody CreateBlogPostRequest request) {
        return ApiDataResponse.ok(blogPostService.createDraft(request));
    }

    @PostMapping("/draft/ai-general")
    @Operation(summary = "일반 블로그 AI 초안 생성", description = "사진 설명, 필수 문구, 메모, 키워드를 기반으로 맛집/카페/여행 등 일반 블로그 초안을 생성합니다.")
    public ApiDataResponse<BlogPostResponse> createGeneralAiDraft(@Valid @RequestBody CreateGeneralBlogPostRequest request) {
        return ApiDataResponse.ok(generalBlogPostService.createAiDraft(request));
    }

    @GetMapping
    @Operation(summary = "블로그 글 목록 조회")
    public ApiDataResponse<List<BlogPostSummaryResponse>> getBlogPosts() {
        return ApiDataResponse.ok(blogPostService.getBlogPosts());
    }

    @GetMapping("/{blogPostId}")
    @Operation(summary = "블로그 글 상세 조회")
    public ApiDataResponse<BlogPostResponse> getBlogPost(@PathVariable Long blogPostId) {
        return ApiDataResponse.ok(blogPostService.getBlogPost(blogPostId));
    }

    @GetMapping("/{blogPostId}/versions")
    @Operation(summary = "블로그 글 버전 이력 조회")
    public ApiDataResponse<List<BlogPostVersionResponse>> getVersions(@PathVariable Long blogPostId) {
        return ApiDataResponse.ok(blogPostService.getVersions(blogPostId));
    }

    @GetMapping("/{blogPostId}/versions/diff")
    @Operation(summary = "블로그 글 버전 diff 조회", description = "수정 전후 버전을 라인 단위로 비교합니다. 버전 번호를 생략하면 최신 버전과 직전 버전을 비교합니다.")
    public ApiDataResponse<BlogPostVersionDiffResponse> getVersionDiff(
            @PathVariable Long blogPostId,
            @RequestParam(required = false) Integer fromVersionNo,
            @RequestParam(required = false) Integer toVersionNo
    ) {
        return ApiDataResponse.ok(blogPostVersionDiffService.diff(blogPostId, fromVersionNo, toVersionNo));
    }

    @PatchMapping("/{blogPostId}")
    @Operation(summary = "블로그 초안 수정")
    public ApiDataResponse<BlogPostResponse> updateDraft(
            @PathVariable Long blogPostId,
            @Valid @RequestBody UpdateBlogPostRequest request
    ) {
        return ApiDataResponse.ok(blogPostService.updateDraft(blogPostId, request));
    }

    @PostMapping("/{blogPostId}/revise/ai")
    @Operation(summary = "AI 추가 요청 기반 블로그 글 수정", description = "현재 초안을 사용자의 추가 요청에 맞춰 다시 작성하고 새 버전으로 저장합니다.")
    public ApiDataResponse<BlogPostResponse> reviseWithAi(
            @PathVariable Long blogPostId,
            @Valid @RequestBody ReviseBlogPostWithAiRequest request
    ) {
        return ApiDataResponse.ok(blogPostRevisionService.reviseWithAi(blogPostId, request));
    }

    @PostMapping("/{blogPostId}/quality-review/ai")
    @Operation(summary = "AI 블로그 품질 리뷰", description = "AI 티, 근거 없는 문장, 읽기 품질, SEO/수익화 준비도를 검수합니다.")
    public ApiDataResponse<BlogPostQualityReviewResponse> reviewQuality(
            @PathVariable Long blogPostId,
            @Valid @RequestBody(required = false) BlogPostQualityReviewRequest request
    ) {
        return ApiDataResponse.ok(blogPostQualityReviewService.review(blogPostId, request));
    }

    @PostMapping("/{blogPostId}/quality-improve/ai")
    @Operation(summary = "AI 블로그 품질 자동 개선", description = "품질 리뷰 결과를 기반으로 AI 재작성을 반복해 발행 전 품질을 개선합니다.")
    public ApiDataResponse<BlogPostQualityImproveResponse> improveQuality(
            @PathVariable Long blogPostId,
            @Valid @RequestBody(required = false) BlogPostQualityImproveRequest request
    ) {
        return ApiDataResponse.ok(blogPostQualityImproveService.improve(blogPostId, request));
    }

    @PostMapping("/{blogPostId}/review-ready")
    @Operation(summary = "블로그 글 검수 대기 처리")
    public ApiDataResponse<BlogPostResponse> markReviewReady(@PathVariable Long blogPostId) {
        return ApiDataResponse.ok(blogPostService.markReviewReady(blogPostId));
    }

    @PostMapping("/{blogPostId}/approve")
    @Operation(summary = "블로그 글 승인")
    public ApiDataResponse<BlogPostResponse> approve(@PathVariable Long blogPostId) {
        return ApiDataResponse.ok(blogPostService.approve(blogPostId));
    }

    @PostMapping("/{blogPostId}/publish")
    @Operation(summary = "블로그 글 발행 완료 처리", description = "실제 GitHub Pages 연동 전까지는 상태만 PUBLISHED로 전환합니다.")
    public ApiDataResponse<BlogPostResponse> markPublished(@PathVariable Long blogPostId) {
        return ApiDataResponse.ok(blogPostService.markPublished(blogPostId));
    }

    @PatchMapping("/{blogPostId}/status")
    @Operation(summary = "블로그 글 상태 수정", description = "발행 후 수정이 필요할 때 PUBLISHED 상태를 DRAFT 또는 APPROVED 등으로 변경할 수 있습니다.")
    public ApiDataResponse<BlogPostResponse> updateStatus(
            @PathVariable Long blogPostId,
            @Valid @RequestBody UpdateBlogPostStatusRequest request
    ) {
        return ApiDataResponse.ok(blogPostService.updateStatus(blogPostId, request));
    }

    @PostMapping("/{blogPostId}/publish/github-pages")
    @Operation(summary = "GitHub Pages 발행", description = "승인된 Markdown 글을 GitHub Pages 저장소의 _posts 경로에 commit합니다.")
    public ApiDataResponse<PublishGithubPagesResponse> publishToGitHubPages(
            @PathVariable Long blogPostId,
            @RequestBody(required = false) PublishGithubPagesRequest request
    ) {
        return ApiDataResponse.ok(blogPostPublishService.publishToGitHubPages(blogPostId, request));
    }

    @PostMapping("/{blogPostId}/export/velog")
    @Operation(summary = "Velog 노출용 Markdown export", description = "승인 또는 발행된 글을 Velog에 복사하기 좋은 Markdown으로 변환합니다.")
    public ApiDataResponse<ExportVelogMarkdownResponse> exportVelogMarkdown(
            @PathVariable Long blogPostId,
            @RequestBody(required = false) ExportVelogMarkdownRequest request
    ) {
        return ApiDataResponse.ok(velogExportService.exportMarkdown(blogPostId, request));
    }
}
