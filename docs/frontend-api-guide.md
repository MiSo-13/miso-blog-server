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

### 블로그 초안 생성

```http
POST /api/blog-posts/draft/manual
```

요청 예시:

```json
{
  "title": "Spring Boot에서 OpenAI 비용 조회 API 붙이기",
  "slug": "spring-openai-cost-api",
  "summary": "OpenAI Admin API로 비용과 사용량을 조회한 구현 기록",
  "contentMarkdown": "# Spring Boot에서 OpenAI 비용 조회 API 붙이기\n\n본문...",
  "tags": ["Spring Boot", "OpenAI", "운영"],
  "sourceNote": "초기에는 수동 입력 기반으로 작성하고, 이후 Git 분석 결과를 연결합니다."
}
```

### 블로그 글 관리

```http
GET /api/blog-posts
GET /api/blog-posts/{blogPostId}
GET /api/blog-posts/{blogPostId}/versions
PATCH /api/blog-posts/{blogPostId}
POST /api/blog-posts/{blogPostId}/review-ready
POST /api/blog-posts/{blogPostId}/approve
POST /api/blog-posts/{blogPostId}/publish
```

상태 흐름:

```text
DRAFT -> REVIEW_READY -> APPROVED -> PUBLISHED
```

`publish`는 아직 실제 GitHub Pages commit을 수행하지 않고, 발행 완료 상태만 표시합니다. 실제 발행 연동은 다음 단계에서 붙입니다.

### 발행 대상

기본 전략은 GitHub Pages를 원본 발행 채널로, Velog를 노출 채널로 둡니다.

```http
GET /api/publish-targets/strategy
POST /api/publish-targets/defaults
GET /api/publish-targets
POST /api/publish-targets
PATCH /api/publish-targets/{targetId}
```

기본 발행 대상 생성:

```http
POST /api/publish-targets/defaults
```

GitHub Pages 대상 생성 예시:

```json
{
  "channel": "GITHUB_PAGES",
  "role": "PRIMARY",
  "name": "My GitHub Pages Blog",
  "baseUrl": "https://blog.example.com",
  "repositoryFullName": "miso/blog.example.com",
  "branchName": "main",
  "contentRootPath": "_posts",
  "customDomain": "blog.example.com",
  "active": true
}
```

Velog 대상 생성 예시:

```json
{
  "channel": "VELOG",
  "role": "EXPOSURE",
  "name": "Velog",
  "baseUrl": "https://velog.io/@username",
  "active": true
}
```

### OpenAI 운영 API

```http
GET /api/admin/openai/summary
GET /api/admin/openai/costs?startDate=2026-05-01&endDate=2026-05-02&groupBy=project_id,line_item
GET /api/admin/openai/usage/completions?startDate=2026-05-01&endDate=2026-05-02&bucketWidth=1d&groupBy=model,api_key_id
GET /api/admin/openai/estimate?model=gpt-4.1-mini&inputTokens=10000&cachedInputTokens=2000&outputTokens=3000
```

## 예정 API 초안

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

## 프론트 구현 메모

- 초기 인증은 아직 열려 있습니다. 로그인/JWT가 도입되면 인증 헤더 규칙을 이 문서에 추가합니다.
- 긴 AI 작업은 즉시 결과를 반환하지 않고 job id를 반환하는 방식으로 설계할 예정입니다.
- 블로그 초안 화면은 Markdown 에디터, 태그 입력, source note, 버전 이력을 함께 보여주면 됩니다.
- 발행 설정 화면은 GitHub Pages 카드와 Velog 카드로 나누고, GitHub Pages를 기본 발행 대상으로 강조하면 됩니다.
- OpenAI 운영 화면은 summary 카드, 비용 일자별 차트, 모델별 token 사용량 표, 예상 비용 계산기를 분리하면 됩니다.
