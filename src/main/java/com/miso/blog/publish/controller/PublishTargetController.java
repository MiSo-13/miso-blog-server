package com.miso.blog.publish.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.publish.dto.CreatePublishTargetRequest;
import com.miso.blog.publish.dto.GitHubPagesConnectionTestResponse;
import com.miso.blog.publish.dto.PublishStrategyResponse;
import com.miso.blog.publish.dto.PublishTargetResponse;
import com.miso.blog.publish.dto.UpdatePublishTargetRequest;
import com.miso.blog.publish.service.PublishTargetService;
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
@RequestMapping("/api/publish-targets")
@Tag(name = "발행 대상", description = "GitHub Pages와 Velog 같은 블로그 발행 채널을 관리합니다.")
public class PublishTargetController {
    private final PublishTargetService publishTargetService;

    @GetMapping
    @Operation(summary = "발행 대상 목록 조회")
    public ApiDataResponse<List<PublishTargetResponse>> getTargets() {
        return ApiDataResponse.ok(publishTargetService.getTargets());
    }

    @GetMapping("/strategy")
    @Operation(summary = "기본 발행 전략 조회")
    public ApiDataResponse<PublishStrategyResponse> getStrategy() {
        return ApiDataResponse.ok(publishTargetService.getStrategy());
    }

    @PostMapping
    @Operation(summary = "발행 대상 생성")
    public ApiDataResponse<PublishTargetResponse> createTarget(@Valid @RequestBody CreatePublishTargetRequest request) {
        return ApiDataResponse.ok(publishTargetService.createTarget(request));
    }

    @PostMapping("/defaults")
    @Operation(summary = "기본 발행 대상 생성", description = "대상이 없으면 GitHub Pages와 Velog 기본 채널을 생성합니다.")
    public ApiDataResponse<List<PublishTargetResponse>> createDefaultTargets() {
        return ApiDataResponse.ok(publishTargetService.createDefaultTargets());
    }

    @PatchMapping("/{targetId}")
    @Operation(summary = "발행 대상 수정")
    public ApiDataResponse<PublishTargetResponse> updateTarget(
            @PathVariable Long targetId,
            @Valid @RequestBody UpdatePublishTargetRequest request
    ) {
        return ApiDataResponse.ok(publishTargetService.updateTarget(targetId, request));
    }

    @PostMapping("/{targetId}/test-github-pages")
    @Operation(summary = "GitHub Pages 발행 설정 연결 테스트", description = "GitHub token, repository, branch, contentRootPath 접근 가능 여부를 확인합니다.")
    public ApiDataResponse<GitHubPagesConnectionTestResponse> testGitHubPagesConnection(@PathVariable Long targetId) {
        return ApiDataResponse.ok(publishTargetService.testGitHubPagesConnection(targetId));
    }
}
