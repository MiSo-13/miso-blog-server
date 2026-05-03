package com.miso.blog.media.controller;

import com.miso.blog.common.api.ApiDataResponse;
import com.miso.blog.media.dto.BlogMediaAssetResponse;
import com.miso.blog.media.dto.BlogMediaBatchUploadResponse;
import com.miso.blog.media.service.BlogMediaAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media/images")
@Tag(name = "블로그 이미지", description = "일반 블로그 작성에 사용할 이미지 파일을 업로드하고 조회합니다.")
public class BlogMediaAssetController {
    private final BlogMediaAssetService blogMediaAssetService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "블로그 이미지 업로드", description = "jpg, png, webp, gif 이미지를 업로드하고 일반 블로그 작성 요청에 사용할 publicUrl을 반환합니다.")
    public ApiDataResponse<BlogMediaAssetResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) String note
    ) {
        return ApiDataResponse.ok(blogMediaAssetService.uploadImage(file, altText, note));
    }

    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "블로그 이미지 여러 장 업로드", description = "여러 이미지를 한 번에 업로드하고 일반 블로그 작성 요청에 사용할 uploadGroupId와 asset 목록을 반환합니다.")
    public ApiDataResponse<BlogMediaBatchUploadResponse> uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) List<String> altTexts,
            @RequestParam(required = false) List<String> notes
    ) {
        return ApiDataResponse.ok(blogMediaAssetService.uploadImages(files, altTexts, notes));
    }

    @GetMapping
    @Operation(summary = "블로그 이미지 목록 조회")
    public ApiDataResponse<List<BlogMediaAssetResponse>> getImages() {
        return ApiDataResponse.ok(blogMediaAssetService.getAssets());
    }

    @GetMapping("/groups")
    @Operation(summary = "블로그 이미지 묶음 조회")
    public ApiDataResponse<List<BlogMediaAssetResponse>> getImagesByGroup(@RequestParam String uploadGroupId) {
        return ApiDataResponse.ok(blogMediaAssetService.getAssetsByGroup(uploadGroupId));
    }
}
