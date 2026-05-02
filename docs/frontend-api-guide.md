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
