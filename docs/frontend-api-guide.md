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

## 서버 상태

```http
GET /api/system/health
```

## 로컬 Git 저장소 분석

기본 추천 흐름입니다. 로컬에 clone된 repo를 서버가 직접 `git log`, `git show`, `git diff`로 읽고, 기본값인 `LOCAL_ONLY` 모드에서는 외부 AI로 코드를 전송하지 않습니다.

### 로컬 저장소 등록

```http
POST /api/local-repositories
```

```json
{
  "name": "magi-platform",
  "localPath": "C:\\pjt\\magi-platform",
  "defaultBranch": "main",
  "description": "개인 프로젝트",
  "active": true
}
```

### 로컬 저장소 목록/상세/수정

```http
GET /api/local-repositories
GET /api/local-repositories/{repositoryId}
PATCH /api/local-repositories/{repositoryId}
```

### LOCAL_ONLY 분석

```http
POST /api/local-repositories/{repositoryId}/analyze
```

```json
{
  "commitLimit": 20,
  "includeUncommittedChanges": true,
  "analysisMode": "LOCAL_ONLY",
  "focus": "내가 구현한 기능과 트러블슈팅 포인트를 개발 블로그 글감으로 많이 뽑아줘",
  "createBlogPost": false
}
```

응답에는 다음 정보가 포함됩니다.

- `sourceSummary`: 로컬 git 명령으로 만든 분석 근거
- `analysisSummary`: 로컬 분석 요약
- `keywords`: 기술 키워드 후보
- `topicCandidates`: 블로그 글감 후보
- `recommendedTitle`: 추천 제목
- `draftMarkdown`: 로컬 분석 기반 Markdown 초안

### OPENAI 분석

```json
{
  "commitLimit": 10,
  "includeUncommittedChanges": false,
  "analysisMode": "OPENAI",
  "focus": "운영에서 겪은 설계 판단 중심으로 정리",
  "createBlogPost": true
}
```

주의: `OPENAI` 모드는 `sourceSummary`를 OpenAI API로 전송합니다. 프론트에서는 실행 전 전송 동의 UI를 보여주는 것이 좋습니다.

### 로컬 분석 결과 조회

```http
GET /api/local-repositories/{repositoryId}/analysis-reports
GET /api/local-repositories/analysis-reports/{reportId}
POST /api/local-repositories/analysis-reports/{reportId}/blog-post
POST /api/local-repositories/analysis-reports/{reportId}/write-blog-post
```

### 선택 키워드 기반 글 작성

분석 결과의 `keywords`와 `topicCandidates` 중 사용자가 원하는 것을 고른 뒤 더 정돈된 블로그 초안을 생성합니다.

```http
POST /api/local-repositories/analysis-reports/{reportId}/write-blog-post
```

```json
{
  "selectedKeywords": ["Spring Boot", "Git", "Markdown"],
  "selectedTopicTitle": "최근 구현 흐름을 코드 변경으로 되짚기",
  "writingFocus": "실제 구현 흐름을 독자가 따라갈 수 있게 정리",
  "audience": "개인 프로젝트를 운영 가능한 서비스로 키우는 개발자",
  "writingMode": "LOCAL_ONLY",
  "markReviewReady": true
}
```

`writingMode`:

- `LOCAL_ONLY`: 외부 전송 없이 로컬 분석 결과만으로 초안을 재구성합니다.
- `OPENAI`: source summary를 OpenAI로 보내 더 풍부한 글을 생성합니다.

## GitHub 저장소 분석

private GitHub 저장소를 GitHub API로 읽고 OpenAI로 분석합니다. 보안상 기본 흐름은 로컬 Git 분석을 권장합니다.

```http
POST /api/git-repositories
GET /api/git-repositories
GET /api/git-repositories/{repositoryId}
PATCH /api/git-repositories/{repositoryId}
POST /api/git-repositories/{repositoryId}/analyze
GET /api/git-repositories/{repositoryId}/analysis-reports
GET /api/git-repositories/analysis-reports/{reportId}
POST /api/git-repositories/analysis-reports/{reportId}/blog-post
POST /api/git-repositories/analysis-reports/{reportId}/write-blog-post
```

## 블로그 글

### 수동 초안 생성

```http
POST /api/blog-posts/draft/manual
```

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

### 글 관리

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

## 발행 대상

기본 전략은 GitHub Pages를 원본 발행 채널로, Velog를 노출 채널로 둡니다.

```http
GET /api/publish-targets/strategy
POST /api/publish-targets/defaults
GET /api/publish-targets
POST /api/publish-targets
PATCH /api/publish-targets/{targetId}
```

## OpenAI 운영 API

```http
GET /api/admin/openai/summary
GET /api/admin/openai/costs?startDate=2026-05-01&endDate=2026-05-02&groupBy=project_id,line_item
GET /api/admin/openai/usage/completions?startDate=2026-05-01&endDate=2026-05-02&bucketWidth=1d&groupBy=model,api_key_id
GET /api/admin/openai/estimate?model=gpt-4.1-mini&inputTokens=10000&cachedInputTokens=2000&outputTokens=3000
```

## 프론트 구현 메모

- 기본 분석 버튼은 `LOCAL_ONLY`로 둡니다.
- `OPENAI` 분석은 “코드 요약이 외부 AI로 전송됩니다” 확인 후 실행하게 만듭니다.
- 분석 결과 화면은 키워드, 글감 후보 카드, 추천 초안 Markdown 미리보기로 나누면 좋습니다.
- 사용자가 키워드와 글감 후보를 선택하면 `write-blog-post`를 호출해 실제 블로그 초안을 생성합니다.
- 글감 후보는 `sourceFiles`를 함께 보여줘야 사용자가 “내가 실제로 구현한 내용”인지 빠르게 확인할 수 있습니다.
- 발행 설정 화면은 GitHub Pages 카드와 Velog 카드로 나누고, GitHub Pages를 기본 발행 대상으로 강조하면 됩니다.
