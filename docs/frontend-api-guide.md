# 프론트 연동 가이드

## 기본 정보

- Base URL: `http://localhost:8010`
- 응답 형식: JSON
- Swagger UI: `http://localhost:8010/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8010/v3/api-docs`

## 공통 성공 응답

```json
{
  "success": true,
  "data": {}
}
```

## 공통 실패 응답

```json
{
  "success": false,
  "code": "BAD_REQUEST",
  "message": "잘못된 요청입니다.",
  "occurredAt": "2026-05-02T17:00:00"
}
```

## 현재 제공 API

### 서버 상태 확인

```http
GET /api/system/health
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "serviceName": "miso-blog-server",
    "checkedAt": "2026-05-02T17:00:00"
  }
}
```

## OpenAI 운영 API

### 비용 요약

```http
GET /api/admin/openai/summary
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "apiKeyConfigured": true,
    "adminKeyConfigured": true,
    "effectiveKeyType": "ADMIN",
    "keyLabel": "sk-...abcd",
    "model": "gpt-4.1-mini",
    "costApiAvailable": true,
    "todayCostUsd": 0.12,
    "monthToDateCostUsd": 3.45,
    "budgetLimitUsd": 20.00,
    "remainingBudgetUsd": 16.55,
    "unavailableReason": null,
    "usageDashboardUrl": "https://platform.openai.com/usage",
    "billingUrl": "https://platform.openai.com/settings/organization/billing/overview"
  }
}
```

### 실제 비용 조회

```http
GET /api/admin/openai/costs?startDate=2026-05-01&endDate=2026-05-02&groupBy=project_id,line_item
```

`groupBy`는 `project_id`, `line_item`을 쉼표로 전달할 수 있습니다.

### Completion 사용량 조회

```http
GET /api/admin/openai/usage/completions?startDate=2026-05-01&endDate=2026-05-02&bucketWidth=1d&groupBy=model,api_key_id
```

`bucketWidth`는 `1m`, `1h`, `1d`를 지원합니다. 프론트 기본값은 `1d`, 기본 그룹은 `model`로 잡으면 됩니다.

### 예상 비용 계산

```http
GET /api/admin/openai/estimate?model=gpt-4.1-mini&inputTokens=10000&cachedInputTokens=2000&outputTokens=3000
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "model": "gpt-4.1-mini",
    "inputTokens": 10000,
    "cachedInputTokens": 2000,
    "billableInputTokens": 8000,
    "outputTokens": 3000,
    "inputPricePerMillionUsd": 0.40,
    "cachedInputPricePerMillionUsd": 0.10,
    "outputPricePerMillionUsd": 1.60,
    "estimatedCostUsd": 0.0082,
    "pricingNote": "공식 가격표 기반의 사전 추정값입니다. 실제 청구 금액은 OpenAI Costs API 기준으로 확인하세요."
  }
}
```

## 예정 API 초안

초기 프론트는 아래 API가 생긴다는 전제로 화면 구조를 잡으면 됩니다. 실제 DTO는 구현하면서 문서에 계속 반영합니다.

### Git 저장소

```http
POST /api/repositories
GET /api/repositories
GET /api/repositories/{repositoryId}
POST /api/repositories/{repositoryId}/sync
```

### 분석 작업

```http
POST /api/analysis/jobs
GET /api/analysis/jobs
GET /api/analysis/jobs/{jobId}
```

### 블로그 글

```http
POST /api/blog-posts/draft
GET /api/blog-posts
GET /api/blog-posts/{postId}
PATCH /api/blog-posts/{postId}
POST /api/blog-posts/{postId}/approve
POST /api/blog-posts/{postId}/publish
```

### 관리자

```http
GET /api/admin/ai-jobs
GET /api/admin/ai-jobs/{jobId}
POST /api/admin/ai-jobs/{jobId}/retry
```

## 프론트 구현 메모

- 초기 인증은 아직 열려 있습니다. 로그인/JWT가 도입되면 인증 헤더 규칙을 이 문서에 추가합니다.
- 긴 AI 작업은 즉시 결과를 반환하지 않고 job id를 반환하는 방식으로 설계할 예정입니다.
- 프론트는 작업 상태를 polling하거나, 추후 SSE/WebSocket으로 전환할 수 있게 상태 조회 UI를 분리해두는 편이 좋습니다.
- 블로그 초안은 Markdown 본문, 제목, 요약, 태그, 근거 source 목록을 함께 보여주는 검수 화면이 필요합니다.
- OpenAI 운영 화면은 summary 카드, 비용 일자별 차트, 모델별 token 사용량 표, 예상 비용 계산기를 분리하면 됩니다.
