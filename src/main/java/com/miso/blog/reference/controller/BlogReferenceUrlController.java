package com.miso.blog.reference.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.reference.code.BlogReferenceType;
import com.miso.blog.reference.dto.BlogReferenceUrlResponse;
import com.miso.blog.reference.dto.CreateBlogReferenceUrlRequest;
import com.miso.blog.reference.dto.UpdateBlogReferenceUrlRequest;
import com.miso.blog.reference.service.BlogReferenceUrlService;
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
@RequestMapping("/api/blog-reference-urls")
@Tag(name = "블로그 레퍼런스 URL", description = "일반/개발 블로그 작성에 참고할 URL을 관리합니다.")
public class BlogReferenceUrlController {
    private final BlogReferenceUrlService blogReferenceUrlService;

    @PostMapping
    @Operation(summary = "레퍼런스 URL 추가")
    public ApiDataResponse<BlogReferenceUrlResponse> createReferenceUrl(@Valid @RequestBody CreateBlogReferenceUrlRequest request) {
        return ApiDataResponse.ok(blogReferenceUrlService.createReferenceUrl(request));
    }

    @GetMapping
    @Operation(summary = "레퍼런스 URL 목록 조회")
    public ApiDataResponse<List<BlogReferenceUrlResponse>> getReferenceUrls(@RequestParam(required = false) BlogReferenceType type) {
        return ApiDataResponse.ok(blogReferenceUrlService.getReferenceUrls(type));
    }

    @PatchMapping("/{referenceUrlId}")
    @Operation(summary = "레퍼런스 URL 수정")
    public ApiDataResponse<BlogReferenceUrlResponse> updateReferenceUrl(
            @PathVariable Long referenceUrlId,
            @Valid @RequestBody UpdateBlogReferenceUrlRequest request
    ) {
        return ApiDataResponse.ok(blogReferenceUrlService.updateReferenceUrl(referenceUrlId, request));
    }

    @DeleteMapping("/{referenceUrlId}")
    @Operation(summary = "레퍼런스 URL 삭제")
    public ApiDataResponse<Void> deleteReferenceUrl(@PathVariable Long referenceUrlId) {
        blogReferenceUrlService.deleteReferenceUrl(referenceUrlId);
        return ApiDataResponse.ok(null);
    }
}
