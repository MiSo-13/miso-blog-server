package com.miso.blog.post.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.post.dto.BlogPostResponse;
import com.miso.blog.post.dto.BlogPostSummaryResponse;
import com.miso.blog.post.dto.BlogPostVersionResponse;
import com.miso.blog.post.dto.CreateBlogPostRequest;
import com.miso.blog.post.dto.UpdateBlogPostRequest;
import com.miso.blog.post.service.BlogPostService;
import com.miso.blog.publish.dto.PublishGithubPagesRequest;
import com.miso.blog.publish.dto.PublishGithubPagesResponse;
import com.miso.blog.publish.service.BlogPostPublishService;
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
@RequestMapping("/api/blog-posts")
@Tag(name = "블로그 글", description = "AI 또는 수동으로 생성한 Markdown 블로그 글을 관리합니다.")
public class BlogPostController {
    private final BlogPostService blogPostService;
    private final BlogPostPublishService blogPostPublishService;

    @PostMapping("/draft/manual")
    @Operation(summary = "수동 블로그 초안 생성", description = "Git/AI 연동 전에도 Markdown 초안을 저장할 수 있습니다.")
    public ApiDataResponse<BlogPostResponse> createManualDraft(@Valid @RequestBody CreateBlogPostRequest request) {
        return ApiDataResponse.ok(blogPostService.createDraft(request));
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

    @PatchMapping("/{blogPostId}")
    @Operation(summary = "블로그 초안 수정")
    public ApiDataResponse<BlogPostResponse> updateDraft(
            @PathVariable Long blogPostId,
            @Valid @RequestBody UpdateBlogPostRequest request
    ) {
        return ApiDataResponse.ok(blogPostService.updateDraft(blogPostId, request));
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

    @PostMapping("/{blogPostId}/publish/github-pages")
    @Operation(summary = "GitHub Pages 발행", description = "승인된 Markdown 글을 GitHub Pages 저장소의 _posts 경로에 commit합니다.")
    public ApiDataResponse<PublishGithubPagesResponse> publishToGitHubPages(
            @PathVariable Long blogPostId,
            @RequestBody(required = false) PublishGithubPagesRequest request
    ) {
        return ApiDataResponse.ok(blogPostPublishService.publishToGitHubPages(blogPostId, request));
    }
}
