package com.miso.blog.admin.controller;

import com.miso.blog.admin.dto.OpenAiCostChartResponse;
import com.miso.blog.admin.dto.OpenAiCostsRequest;
import com.miso.blog.admin.dto.OpenAiEstimateRequest;
import com.miso.blog.admin.dto.OpenAiEstimateResponse;
import com.miso.blog.admin.dto.OpenAiSummaryResponse;
import com.miso.blog.admin.dto.OpenAiUsageRequest;
import com.miso.blog.admin.dto.OpenAiUsageResponse;
import com.miso.blog.admin.service.OpenAiOperationsService;
import com.miso.blog.common.api.ApiDataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/openai")
@Tag(name = "OpenAI 운영", description = "OpenAI API Key, 사용량, 비용, 예상 비용을 조회하는 운영 API")
public class AdminOpenAiController {
    private final OpenAiOperationsService openAiOperationsService;

    @GetMapping("/summary")
    @Operation(summary = "OpenAI 비용 요약 조회", description = "오늘 비용, 이번 달 비용, 예산 잔액, 키 설정 상태를 조회합니다.")
    public ApiDataResponse<OpenAiSummaryResponse> getSummary() {
        return ApiDataResponse.ok(openAiOperationsService.getSummary());
    }

    @GetMapping("/costs")
    @Operation(summary = "OpenAI 실제 비용 조회", description = "OpenAI Costs API 기준의 일자별 실제 비용을 조회합니다.")
    public ApiDataResponse<OpenAiCostChartResponse> getCosts(@Valid OpenAiCostsRequest request) {
        return ApiDataResponse.ok(openAiOperationsService.getCosts(request));
    }

    @GetMapping("/usage/completions")
    @Operation(summary = "OpenAI completion 사용량 조회", description = "모델, API key, project 기준 token 사용량을 조회합니다.")
    public ApiDataResponse<OpenAiUsageResponse> getCompletionUsage(@Valid OpenAiUsageRequest request) {
        return ApiDataResponse.ok(openAiOperationsService.getCompletionUsage(request));
    }

    @GetMapping("/estimate")
    @Operation(summary = "OpenAI 예상 비용 계산", description = "모델과 예상 token 수를 기준으로 호출 1회의 예상 비용을 계산합니다.")
    public ApiDataResponse<OpenAiEstimateResponse> estimate(@Valid OpenAiEstimateRequest request) {
        return ApiDataResponse.ok(openAiOperationsService.estimate(request));
    }
}
