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

## AI 비동기 작업

AI 글 생성/수정처럼 시간이 걸릴 수 있는 작업은 비동기 job API를 우선 사용합니다. 요청 즉시 `jobId`를 받고, 프론트는 상태를 polling해서 완료 결과를 표시합니다.

상태:

```text
PENDING -> RUNNING -> SUCCEEDED
                  -> FAILED
```

### 일반 블로그 생성 job

```http
POST /api/ai-jobs/blog-posts/draft/ai-general
```

요청 body는 `POST /api/blog-posts/draft/ai-general`과 같습니다.

### GitHub 저장소 분석 job

```http
POST /api/ai-jobs/git-repositories/{repositoryId}/analyze
```

요청 body는 `POST /api/git-repositories/{repositoryId}/analyze`와 같습니다. GitHub commit 조회와 OpenAI 분석은 1분 이상 걸릴 수 있으므로, 프론트에서는 동기 분석 API보다 이 job API를 기본으로 사용하세요.

전체 commit을 훑고 싶으면 `analyzeAllCommits=true`를 보냅니다. 서버는 운영 보호를 위해 `GITHUB_ANALYSIS_MAX_ALL_COMMITS` 설정값까지만 조회합니다. 기본값은 300개입니다.

```json
{
  "analyzeAllCommits": true,
  "focus": "예전 구현과 최근 변경을 함께 보고 블로그 글감을 많이 찾아줘",
  "createBlogPost": false
}
```

### AI 추가 수정 job

```http
POST /api/ai-jobs/blog-posts/{blogPostId}/revise/ai
```

요청 body는 `POST /api/blog-posts/{blogPostId}/revise/ai`와 같습니다.

### AI 품질 자동 개선 job

```http
POST /api/ai-jobs/blog-posts/{blogPostId}/quality-improve/ai
```

요청 body는 `POST /api/blog-posts/{blogPostId}/quality-improve/ai`와 같습니다. 리뷰, 재작성, 재리뷰가 이어질 수 있으므로 프론트에서는 이 비동기 API를 기본으로 사용하는 것을 권장합니다.

### 작업 상태 조회

```http
GET /api/ai-jobs/{jobId}
GET /api/ai-jobs
POST /api/ai-jobs/{jobId}/retry
```

응답 예시:

```json
{
  "id": 1,
  "type": "GENERAL_BLOG_DRAFT",
  "status": "SUCCEEDED",
  "resultBlogPostId": 10,
  "resultJson": "{\"id\":10,\"title\":\"성수동 파스타 맛집 후기\"}",
  "errorMessage": null,
  "failure": null,
  "retryable": false,
  "retryCount": 0,
  "retriedFromJobId": null,
  "startedAt": "2026-05-03T11:30:00",
  "finishedAt": "2026-05-03T11:30:20",
  "createdAt": "2026-05-03T11:29:59",
  "updatedAt": "2026-05-03T11:30:20"
}
```

프론트 권장 흐름:

1. job 생성 API 호출
2. `PENDING`/`RUNNING` 동안 로딩 상태 표시
3. 1~3초 간격으로 `GET /api/ai-jobs/{jobId}` polling
4. `SUCCEEDED`이면 `resultBlogPostId`로 `GET /api/blog-posts/{blogPostId}` 호출
5. `FAILED`이면 `errorMessage`를 보여주고 재시도 버튼 제공

실패 응답 예시:

```json
{
  "id": 12,
  "type": "BLOG_POST_QUALITY_IMPROVE",
  "status": "FAILED",
  "errorMessage": "OpenAI 요청 한도를 초과했습니다.",
  "failure": {
    "code": "OPENAI_RATE_LIMIT",
    "message": "OpenAI 요청 한도를 초과했습니다.",
    "detailMessage": "OpenAI 블로그 작성 호출에 실패했습니다. status=429",
    "retryable": true,
    "actionGuide": "잠시 후 다시 시도하거나 모델, 요청량, 결제 한도를 확인하세요.",
    "failedAt": "2026-05-03T12:30:00"
  },
  "retryable": true,
  "retryCount": 0,
  "retriedFromJobId": null
}
```

재시도:

```http
POST /api/ai-jobs/{jobId}/retry
```

재시도 API는 기존 실패 job을 다시 실행하지 않고 같은 요청으로 새 job을 만듭니다. 응답의 새 `id`를 기준으로 다시 polling하세요. `retryable=false`이면 설정, 입력값, 글 상태를 먼저 고쳐야 하므로 재시도 버튼을 숨기거나 비활성화하는 것이 좋습니다.

기존 동기 API도 유지하지만, 프론트에서는 비동기 job API를 기본으로 쓰는 것을 권장합니다.

## 로컬 Git 저장소 분석

기본 추천 흐름입니다. 로컬에 clone된 repo를 서버가 직접 `git log`, `git show`, `git diff`로 읽고, 기본값인 `LOCAL_ONLY` 모드에서는 외부 AI로 코드를 전송하지 않습니다.

중요한 전제:

- `localPath`는 사용자 브라우저의 PC 경로가 아니라 서버가 직접 접근할 수 있는 경로입니다.
- Docker 배포에서는 Windows의 `C:\...` 경로가 컨테이너 안에 없으므로 직접 등록하면 `로컬 저장소 경로가 디렉터리가 아닙니다.` 오류가 날 수 있습니다.
- Docker에서는 아래 “GitHub 저장소 선택 및 clone” API로 repo를 컨테이너 내부에 내려받은 뒤 분석하는 흐름을 권장합니다.

### 로컬 저장소 등록

```http
GET /api/local-repositories/defaults
```

`application-private.yml`의 `blog.local-repositories.defaults`에 적힌 로컬 프로젝트 후보를 반환합니다. 프론트에서는 이 목록을 먼저 보여주고, 사용자가 선택하면 `POST /api/local-repositories`로 등록하면 됩니다. `readable=false`인 항목은 서버가 해당 경로를 Git 저장소로 읽지 못한 상태이므로 등록 버튼을 비활성화하고 `message`를 보여주세요.

응답 예시:

```json
[
  {
    "name": "magi-platform",
    "localPath": "C:\\pjt\\magi-platform",
    "normalizedLocalPath": "C:\\pjt\\magi-platform",
    "defaultBranch": "main",
    "description": "MAGI 참고 프로젝트",
    "active": true,
    "readable": true,
    "registered": false,
    "message": "읽을 수 있는 로컬 Git 저장소입니다."
  }
]
```

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

### GitHub 저장소 선택 및 Docker 내부 clone

Docker 배포에서 추천하는 개발 블로그 흐름입니다.

```http
GET /api/local-repositories/github/repositories
GET /api/local-repositories/github/branches?repositoryFullName=owner/repo
POST /api/local-repositories/github/clone
```

저장소 목록 조회 응답 예시:

```json
[
  {
    "name": "magi-platform",
    "fullName": "user/magi-platform",
    "ownerLogin": "user",
    "defaultBranch": "main",
    "privateRepository": true,
    "fork": false,
    "githubPagesCandidate": false,
    "htmlUrl": "https://github.com/user/magi-platform",
    "updatedAt": "2026-05-03T12:00:00Z"
  }
]
```

브랜치 목록 조회 응답 예시:

```json
[
  {
    "name": "main",
    "commitSha": "abc123",
    "protectedBranch": false
  }
]
```

clone 요청 예시:

```json
{
  "repositoryFullName": "user/magi-platform",
  "branchName": "main",
  "name": "magi-platform",
  "description": "GitHub에서 clone한 개발 블로그 분석 대상",
  "refreshExisting": true
}
```

clone 응답은 `LocalRepositoryResponse`입니다. 프론트는 응답의 `id`를 사용해서 곧바로 분석 API를 호출하면 됩니다.

```http
POST /api/local-repositories/{repositoryId}/analyze
```

프론트 권장 UI:

- 저장소 목록에서 `privateRepository=true`를 표시합니다.
- 저장소 선택 후 branch select를 보여줍니다.
- 이미 clone된 저장소는 `refreshExisting=true`로 최신화 버튼을 제공하면 좋습니다.
- clone 실패 시 GitHub token 권한, branch 이름, Docker의 Git 설치 여부를 안내합니다.

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

- `sourceSummary`: 로컬 git 명령으로 만든 분석 근거. 서버에서 secret masking 처리 후 저장/응답합니다.
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

주의: `OPENAI` 모드는 secret masking 된 `sourceSummary`를 OpenAI API로 전송합니다. 프론트에서는 실행 전 전송 동의 UI를 보여주는 것이 좋습니다.

### Secret masking

분석 근거에는 diff와 설정 파일 일부가 섞일 수 있으므로 서버는 저장 전과 OpenAI 전송 전에 민감값을 자동 치환합니다.

- OpenAI key, GitHub token
- `Authorization: Bearer ...`
- `password`, `secret`, `token`, `api-key` 계열 설정값
- private key block
- JDBC URL 안의 비밀번호

프론트는 `sourceSummary`를 미리보기로 보여줄 수 있지만, 사용자가 `OPENAI` 모드를 실행하기 전에는 “마스킹된 분석 근거가 외부 AI로 전송된다”는 안내를 유지해주세요.

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
POST /api/ai-jobs/git-repositories/{repositoryId}/analyze
GET /api/git-repositories/{repositoryId}/analysis-reports
GET /api/git-repositories/analysis-reports/{reportId}
POST /api/git-repositories/analysis-reports/{reportId}/blog-post
POST /api/git-repositories/analysis-reports/{reportId}/write-blog-post
```

프록시나 다른 서버를 거쳐 호출하는 운영 환경에서는 `POST /api/git-repositories/{repositoryId}/analyze` 동기 API가 504 timeout을 만날 수 있습니다. 프론트에서는 아래 비동기 흐름을 사용하세요.

1. `POST /api/ai-jobs/git-repositories/{repositoryId}/analyze`
2. 응답의 `id`를 jobId로 저장
3. `GET /api/ai-jobs/{jobId}`를 1~3초 간격으로 polling
4. `status=SUCCEEDED`이면 `resultJson` 안의 분석 결과를 표시
5. `resultBlogPostId`가 있으면 글 상세로 이동 가능
6. `status=FAILED`이면 `failure.message`와 `failure.actionGuide` 표시

분석 요청 옵션:

| 필드 | 설명 |
| --- | --- |
| `commitLimit` | 최근 몇 개 commit을 볼지. 기본값 10, 최대 300 |
| `analyzeAllCommits` | true면 `commitLimit` 대신 전체 분석 모드 사용 |
| `focus` | 분석 방향 |
| `createBlogPost` | true면 분석 성공 후 초안까지 생성 |

`analyzeAllCommits=true`는 실제 전체 이력을 대상으로 하되, 서버 설정 `blog.github.analysis.max-all-commits`까지만 조회합니다. 기본값은 300입니다. 오래된 commit까지 보고 싶을 때 사용하고, 프론트에서는 “전체 분석은 오래 걸릴 수 있음” 안내를 보여주세요.

## 블로그 글

### 이전 글 자동 참고

AI가 새 글을 작성하거나 기존 글을 수정/첨삭할 때 서버는 최근 저장된 블로그 글 일부를 자동으로 참고합니다. 프론트에서 별도 필드를 보낼 필요는 없습니다.

적용 대상:

- `POST /api/blog-posts/draft/ai-general`
- `POST /api/ai-jobs/blog-posts/draft/ai-general`
- `POST /api/git-repositories/analysis-reports/{reportId}/write-blog-post`
- `POST /api/local-repositories/analysis-reports/{reportId}/write-blog-post`
- `POST /api/blog-posts/{blogPostId}/revise/ai`
- `POST /api/blog-posts/{blogPostId}/quality-review/ai`
- `POST /api/blog-posts/{blogPostId}/quality-improve/ai`

프론트 안내 문구 예시:

> 이전에 저장한 글의 문체와 구성을 참고해서 더 일관된 블로그 톤으로 작성합니다.

## 블로그 이미지

일반 블로그 작성에 사용할 이미지는 먼저 서버에 업로드한 뒤, 응답의 `publicUrl`을 `POST /api/blog-posts/draft/ai-general` 요청의 `photos[].url`에 넣으면 됩니다.

```http
POST /api/media/images
Content-Type: multipart/form-data
```

Form data:

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `file` | Y | jpg, png, webp, gif 이미지 |
| `altText` | N | 이미지 대체 텍스트 |
| `note` | N | 블로그 작성에 참고할 사진 메모 |

응답 예시:

```json
{
  "id": 1,
  "originalFilename": "pasta.jpg",
  "storedFilename": "uuid.jpg",
  "contentType": "image/jpeg",
  "fileSize": 123456,
  "relativePath": "2026/05/03/uuid.jpg",
  "publicUrl": "/media/2026/05/03/uuid.jpg",
  "uploadGroupId": null,
  "altText": "트러플 크림 파스타",
  "note": "메뉴 설명 문단 근처에 배치",
  "createdAt": "2026-05-03T11:00:00",
  "updatedAt": "2026-05-03T11:00:00"
}
```

```http
GET /api/media/images
```

업로드된 이미지 목록을 최신순으로 조회합니다.

### 여러 장 업로드

일반 블로그 작성 화면에서는 여러 사진을 한 번에 선택해서 업로드하는 흐름을 권장합니다. 서버는 같은 요청으로 올라온 사진에 동일한 `uploadGroupId`를 부여합니다. 이후 일반 블로그 작성 요청에서 `photoGroupId`만 보내면 서버가 해당 묶음의 사진 URL과 설명을 자동으로 본문 작성 자료에 포함합니다.

```http
POST /api/media/images/batch
Content-Type: multipart/form-data
```

Form data:

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `files` | Y | 여러 이미지 파일. 같은 key로 반복 전송 |
| `altTexts` | N | 파일 순서에 맞춘 대체 텍스트 목록 |
| `notes` | N | 파일 순서에 맞춘 사진 메모 목록 |

응답 예시:

```json
{
  "uploadGroupId": "7f9d1f4c-7f4f-4f43-95aa-1f9f2d3f9d11",
  "uploadedCount": 2,
  "assets": [
    {
      "id": 10,
      "originalFilename": "outside.jpg",
      "publicUrl": "/media/2026/05/03/outside.jpg",
      "uploadGroupId": "7f9d1f4c-7f4f-4f43-95aa-1f9f2d3f9d11",
      "altText": "가게 외관",
      "note": "도입부 근처에 배치"
    },
    {
      "id": 11,
      "originalFilename": "pasta.jpg",
      "publicUrl": "/media/2026/05/03/pasta.jpg",
      "uploadGroupId": "7f9d1f4c-7f4f-4f43-95aa-1f9f2d3f9d11",
      "altText": "트러플 크림 파스타",
      "note": "메뉴 설명 문단에 배치"
    }
  ]
}
```

사진 묶음 조회:

```http
GET /api/media/images/groups?uploadGroupId=7f9d1f4c-7f4f-4f43-95aa-1f9f2d3f9d11
```

프론트 권장 동작:

- 사용자가 여러 사진을 선택하면 `POST /api/media/images/batch`를 호출합니다.
- 응답의 `uploadGroupId`를 일반 블로그 작성 form 상태에 저장합니다.
- 업로드 직후 썸네일은 `assets[].publicUrl`로 표시합니다.
- 글 작성 요청에는 `photoGroupId` 또는 개별 `photoAssetIds`를 보냅니다.
- 사진별 설명/메모는 현재 업로드 전에 입력받아 `altTexts`, `notes`로 함께 전송하는 방식을 사용합니다.

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

### 일반 블로그 AI 초안 생성

맛집, 식당, 카페, 여행, 제품 리뷰, 일상 글처럼 Git 분석과 무관한 일반 블로그 초안을 생성합니다. 사용자가 사진 URL/설명, 꼭 넣어야 할 문구, 메모, 키워드를 입력하면 AI가 전체 Markdown 초안을 만들고 기존 `BlogPost`로 저장합니다.

```http
POST /api/blog-posts/draft/ai-general
```

```json
{
  "category": "RESTAURANT",
  "titleHint": "성수동 파스타 맛집 후기",
  "placeName": "성수 라비올리",
  "addressHint": "서울 성동구 성수동",
  "requiredPhrases": [
    "예약하고 방문하는 걸 추천합니다",
    "트러플 크림 파스타가 특히 좋았습니다"
  ],
  "memo": "주말 저녁 방문. 내부는 조용했고 데이트하기 좋음. 가격은 조금 있지만 만족도 높음.",
  "keywords": ["성수 맛집", "파스타", "데이트", "트러플"],
  "photoGroupId": "7f9d1f4c-7f4f-4f43-95aa-1f9f2d3f9d11",
  "photos": [
    {
      "url": "/media/2026/05/03/outside.jpg",
      "description": "가게 외관 사진",
      "placementNote": "도입부 근처"
    },
    {
      "url": "/media/2026/05/03/pasta.jpg",
      "description": "트러플 크림 파스타 사진",
      "placementNote": "메뉴 설명 문단"
    }
  ],
  "imagePlacementNotes": "사진 자리표시자는 [사진: 설명] 형태로 본문 중간에 넣기",
  "tone": "친근하고 자연스러운 후기체",
  "audience": "성수동 데이트 맛집을 찾는 독자",
  "targetLength": "LONG",
  "markReviewReady": true
}
```

사진 입력 방식:

- `photoGroupId`: 여러 장 업로드 응답의 묶음 ID입니다. 가장 추천하는 방식입니다.
- `photoAssetIds`: 사용자가 이미지 목록에서 고른 특정 사진 ID 목록입니다.
- `photos`: 외부 이미지 URL이나 임시 자리표시 설명을 직접 넘길 때 사용합니다.

`photoGroupId`와 `photos`를 함께 보내면 서버는 둘을 합쳐서 AI 작성 자료로 사용합니다. 같은 asset이 `photoAssetIds`와 `photoGroupId`에 중복되어도 한 번만 포함합니다.

`category`:

- `RESTAURANT`
- `CAFE`
- `TRAVEL`
- `PRODUCT_REVIEW`
- `DAILY`
- `ETC`

`targetLength`:

- `SHORT`
- `MEDIUM`
- `LONG`

응답은 기존 `BlogPostResponse`와 동일합니다. 프론트에서는 생성 직후 Markdown 미리보기, 검수 대기 여부, GitHub Pages 발행/Velog export 버튼을 같은 화면에서 이어주면 됩니다.

### 글 관리

```http
GET /api/blog-posts
GET /api/blog-posts/{blogPostId}
GET /api/blog-posts/{blogPostId}/versions
GET /api/blog-posts/{blogPostId}/versions/diff
PATCH /api/blog-posts/{blogPostId}
POST /api/blog-posts/{blogPostId}/revise/ai
POST /api/blog-posts/{blogPostId}/quality-review/ai
POST /api/blog-posts/{blogPostId}/quality-improve/ai
POST /api/blog-posts/{blogPostId}/review-ready
POST /api/blog-posts/{blogPostId}/approve
POST /api/blog-posts/{blogPostId}/publish
POST /api/blog-posts/{blogPostId}/publish/github-pages
POST /api/blog-posts/{blogPostId}/export/velog
```

상태 흐름:

```text
DRAFT -> REVIEW_READY -> APPROVED -> PUBLISHED
```

### AI 결과 편집/추가 요청

개발 블로그 분석 결과나 일반 블로그 AI 생성 결과를 프론트에서 보여준 뒤, 사용자는 두 가지 방식으로 다듬을 수 있습니다.

- 직접 편집: `PATCH /api/blog-posts/{blogPostId}`
- 추가 요청으로 AI 재작성: `POST /api/blog-posts/{blogPostId}/revise/ai`

```http
POST /api/blog-posts/{blogPostId}/revise/ai
```

```json
{
  "revisionInstruction": "도입부를 더 자연스럽게 바꾸고, 실제 방문 후기 느낌을 살려줘. 가격 이야기는 과장하지 말아줘.",
  "additionalMemo": "주말 저녁이라 사람이 조금 있었지만 대화하기 불편할 정도는 아니었음.",
  "tone": "친근하고 담백한 후기체",
  "targetLength": "LONG",
  "preserveTitle": false,
  "preserveTags": true,
  "markReviewReady": true
}
```

응답은 수정된 `BlogPostResponse`입니다. 서버는 기존 글을 새 버전으로 저장하므로 `GET /api/blog-posts/{blogPostId}/versions`에서 이전 초안과 AI 수정본을 비교할 수 있습니다.

### 수정 전후 diff

```http
GET /api/blog-posts/{blogPostId}/versions/diff?fromVersionNo=1&toVersionNo=2
```

`fromVersionNo`, `toVersionNo`를 생략하면 최신 버전과 직전 버전을 비교합니다. 프론트에서는 `sections[].fieldName` 별로 제목, 요약, 본문, 태그 변경을 나눠 보여주고, `lines[].type`이 `INSERT`면 추가, `DELETE`면 삭제, `EQUAL`이면 변경 없는 줄로 렌더링하면 됩니다.

응답 예시:

```json
{
  "blogPostId": 6,
  "fromVersionNo": 2,
  "toVersionNo": 3,
  "addedLineCount": 19,
  "deletedLineCount": 27,
  "changed": true,
  "sections": [
    {
      "fieldName": "contentMarkdown",
      "changed": true,
      "addedLineCount": 17,
      "deletedLineCount": 25,
      "lines": [
        {"type": "DELETE", "oldLineNo": 1, "newLineNo": null, "text": "이전 문장"},
        {"type": "INSERT", "oldLineNo": null, "newLineNo": 1, "text": "수정된 문장"}
      ]
    }
  ]
}
```

발행된 글은 AI 수정으로 덮어쓸 수 없습니다. 발행 후 수정이 필요하면 새 글 또는 별도 수정 발행 정책을 두는 것이 안전합니다.

### GitHub Pages 발행

승인된 글은 GitHub Pages 저장소의 `_posts` 경로에 Jekyll Markdown 파일로 commit할 수 있습니다.

```http
POST /api/blog-posts/{blogPostId}/publish/github-pages
```

```json
{
  "targetId": 1,
  "commitMessage": "Publish post: Spring Boot OpenAI 비용 조회"
}
```

`targetId`를 생략하면 활성화된 `GITHUB_PAGES` 대상 중 `PRIMARY` 역할을 우선 사용합니다.

응답 예시:

```json
{
  "blogPostId": 10,
  "status": "PUBLISHED",
  "targetId": 1,
  "repositoryFullName": "user/user.github.io",
  "branchName": "main",
  "filePath": "_posts/2026-05-02-spring-openai-cost.md",
  "commitSha": "abc123",
  "commitUrl": "https://github.com/user/user.github.io/commit/abc123",
  "contentUrl": "https://github.com/user/user.github.io/blob/main/_posts/2026-05-02-spring-openai-cost.md",
  "expectedPublicUrl": "https://blog.example.com/2026/05/02/spring-openai-cost.html"
}
```

프론트에서는 `APPROVED` 상태의 글에만 GitHub Pages 발행 버튼을 노출하면 됩니다.

### Velog 노출용 export

Velog는 노출 채널로 사용하므로, 서버는 자동 발행 대신 복사/업로드하기 쉬운 Markdown을 내려줍니다. 승인 또는 발행된 글만 export할 수 있습니다.

```http
POST /api/blog-posts/{blogPostId}/export/velog
```

```json
{
  "targetId": 2,
  "canonicalUrl": "https://blog.example.com/2026/05/02/spring-openai-cost.html",
  "includeCanonicalLink": true,
  "includeSourceNote": true
}
```

`targetId`는 선택값입니다. 생략하면 활성화된 `VELOG` 대상이 있으면 응답에 함께 표시하고, 없어도 Markdown export는 가능합니다.

응답 예시:

```json
{
  "blogPostId": 10,
  "targetId": 2,
  "targetName": "Velog",
  "title": "Spring Boot에서 OpenAI 비용 조회 API 붙이기",
  "summary": "OpenAI Admin API로 비용과 사용량을 조회한 구현 기록",
  "tags": ["Spring Boot", "OpenAI", "운영"],
  "markdown": "> 원본 글: [Spring Boot에서 OpenAI 비용 조회 API 붙이기](https://blog.example.com/2026/05/02/spring-openai-cost.html)\n\n# ...",
  "canonicalUrl": "https://blog.example.com/2026/05/02/spring-openai-cost.html",
  "guide": "Velog 글쓰기 화면에 title, markdown, tags를 복사해 노출용 글로 발행하면 됩니다."
}
```

프론트에서는 `title`, `markdown`, `tags` 각각에 복사 버튼을 두면 좋습니다.

## 발행 대상

기본 전략은 GitHub Pages를 원본 발행 채널로, Velog를 노출 채널로 둡니다.

```http
GET /api/publish-targets/strategy
POST /api/publish-targets/defaults
GET /api/publish-targets
POST /api/publish-targets
PATCH /api/publish-targets/{targetId}
GET /api/publish-targets/github/repositories
GET /api/publish-targets/github/branches?repositoryFullName=user/user.github.io
POST /api/publish-targets/{targetId}/test-github-pages
```

기본 흐름은 `application-private.yml`에 GitHub token과 owner만 넣고, 프론트에서 저장소와 브랜치를 선택하는 방식입니다.

### GitHub 저장소 선택

```http
GET /api/publish-targets/github/repositories
```

private 설정의 `github.owner`에 해당하는 접근 가능한 저장소 목록을 반환합니다. GitHub Pages 후보 저장소는 `githubPagesCandidate=true`로 표시됩니다.

응답 예시:

```json
[
  {
    "name": "user.github.io",
    "fullName": "user/user.github.io",
    "ownerLogin": "user",
    "defaultBranch": "main",
    "privateRepository": false,
    "fork": false,
    "githubPagesCandidate": true,
    "htmlUrl": "https://github.com/user/user.github.io",
    "updatedAt": "2026-05-03T12:00:00Z"
  }
]
```

### GitHub 브랜치 선택

```http
GET /api/publish-targets/github/branches?repositoryFullName=user/user.github.io
```

선택한 저장소의 브랜치 목록을 반환합니다. 프론트에서는 저장소 선택 후 이 API를 호출하고, 사용자가 선택한 branch를 `PATCH /api/publish-targets/{targetId}`에 저장하면 됩니다.

응답 예시:

```json
[
  {
    "name": "main",
    "commitSha": "abc123",
    "protectedBranch": false
  }
]
```

GitHub Pages 대상에는 최종적으로 다음 값이 저장되어야 합니다.

```json
{
  "channel": "GITHUB_PAGES",
  "role": "PRIMARY",
  "name": "GitHub Pages",
  "baseUrl": "https://blog.example.com",
  "repositoryFullName": "user/user.github.io",
  "branchName": "main",
  "contentRootPath": "_posts",
  "customDomain": "blog.example.com",
  "active": true
}
```

### GitHub Pages 연결 테스트

```http
POST /api/publish-targets/{targetId}/test-github-pages
```

실제 발행 전에 GitHub token, repositoryFullName, branchName, contentRootPath 접근 가능 여부를 확인합니다. 이 API는 GitHub에 파일을 쓰지 않고 읽기 요청만 수행합니다.

성공 응답 예시:

```json
{
  "targetId": 1,
  "repositoryFullName": "user/user.github.io",
  "branchName": "main",
  "contentRootPath": "_posts",
  "success": true,
  "checkedItems": ["repository", "branch", "contentRootPath"],
  "warnings": [],
  "repositoryUrl": "https://github.com/user/user.github.io",
  "branchUrl": "https://github.com/user/user.github.io/tree/main",
  "contentRootUrl": "https://github.com/user/user.github.io/tree/main/_posts",
  "message": "GitHub Pages 발행 설정 연결이 정상입니다.",
  "checkedAt": "2026-05-03T12:40:00"
}
```

프론트에서는 발행 설정 화면에 테스트 버튼을 두고, 성공 전까지 실제 발행 버튼을 비활성화하는 것을 권장합니다. 현재 target에 `repositoryFullName`이 비어 있어도 서버 private 설정의 `github.owner`가 있으면 `owner/owner.github.io`로 추론합니다.

## OpenAI 운영 API

```http
GET /api/admin/openai/summary
GET /api/admin/openai/costs?startDate=2026-05-01&endDate=2026-05-02&groupBy=project_id,line_item
GET /api/admin/openai/usage/completions?startDate=2026-05-01&endDate=2026-05-02&bucketWidth=1d&groupBy=model,api_key_id
GET /api/admin/openai/estimate?model=gpt-4.1-mini&inputTokens=10000&cachedInputTokens=2000&outputTokens=3000
```

## 프론트 구현 메모

- 기본 분석 버튼은 `LOCAL_ONLY`로 둡니다.
- 로컬 저장소 화면은 `GET /api/local-repositories/defaults` 후보를 먼저 보여주고, 사용자가 고른 항목을 기존 등록 API로 저장하게 만듭니다.
- `OPENAI` 분석은 “마스킹된 코드 요약이 외부 AI로 전송됩니다” 확인 후 실행하게 만듭니다.
- 분석 결과 화면은 키워드, 글감 후보 카드, 추천 초안 Markdown 미리보기로 나누면 좋습니다.
- 사용자가 키워드와 글감 후보를 선택하면 `write-blog-post`를 호출해 실제 블로그 초안을 생성합니다.
- AI가 만든 초안 화면에는 Markdown 직접 편집 영역과 추가 요청 입력창을 함께 둡니다.
- 추가 요청 입력창은 `revise/ai`를 호출하고, 결과를 다시 미리보기/편집 화면에 반영합니다.
- 글감 후보는 `sourceFiles`를 함께 보여줘야 사용자가 “내가 실제로 구현한 내용”인지 빠르게 확인할 수 있습니다.
- 발행 설정 화면은 GitHub Pages 카드와 Velog 카드로 나누고, GitHub Pages를 기본 발행 대상으로 강조하면 됩니다.

## 레퍼런스 URL 관리

개발 블로그와 일반 블로그에서 계속 참고할 외부 URL을 저장해두는 기능입니다. 개발 블로그는 공식 문서, 장애 리포트, 라이브러리 레퍼런스, GitHub 이슈 링크를 저장할 수 있고, 일반 블로그는 식당 공식 페이지, 지도 링크, 메뉴판, 예약 페이지, 참고한 기사나 리뷰 링크를 저장할 수 있습니다.

저장된 레퍼런스 URL은 다음 AI 작업에 자동으로 포함됩니다.

- 일반 블로그 AI 초안 생성: `GENERAL` 레퍼런스 참고
- 개발 블로그 AI 초안 생성: `DEVELOPMENT` 레퍼런스 참고
- AI 추가 수정: `GENERAL`, `DEVELOPMENT` 레퍼런스 모두 참고
- AI 품질 리뷰/자동 개선: `GENERAL`, `DEVELOPMENT` 레퍼런스 모두 참고

서버가 URL 본문을 직접 크롤링하지는 않습니다. AI에는 사용자가 저장한 `title`, `url`, `description`, `tags`만 전달됩니다. 프론트에서는 URL만 넣기보다 “왜 참고해야 하는지”를 `description`에 짧게 적게 해주는 UI가 좋습니다.

### 레퍼런스 타입

| 값 | 용도 |
| --- | --- |
| `DEVELOPMENT` | 개발 블로그용 공식 문서, 기술 글, GitHub 이슈, 장애 분석 링크 |
| `GENERAL` | 맛집/식당/카페/여행/제품 리뷰 등 일반 블로그용 링크 |

### 레퍼런스 URL 추가

```http
POST /api/blog-reference-urls
```

```json
{
  "type": "DEVELOPMENT",
  "title": "Spring Boot Reference Documentation",
  "url": "https://docs.spring.io/spring-boot/index.html",
  "description": "Spring Boot 설정과 운영 옵션을 설명할 때 참고할 공식 문서",
  "tags": ["Spring Boot", "공식문서", "설정"],
  "active": true
}
```

일반 블로그 예시:

```json
{
  "type": "GENERAL",
  "title": "네이버 지도 매장 페이지",
  "url": "https://map.naver.com/example",
  "description": "위치와 영업시간 확인용. 실제 방문 후기는 사용자가 입력한 메모를 우선한다.",
  "tags": ["맛집", "지도", "영업시간"],
  "active": true
}
```

### 레퍼런스 URL 목록 조회

```http
GET /api/blog-reference-urls
GET /api/blog-reference-urls?type=DEVELOPMENT
GET /api/blog-reference-urls?type=GENERAL
```

프론트 권장 UI:

- 타입 탭: `개발`, `일반`
- 검색/필터: 제목, 태그, 활성 여부
- 활성 토글: AI 참고 여부를 빠르게 켜고 끄기
- 설명 입력란: AI가 URL을 어떻게 참고해야 하는지 적는 메모

### 레퍼런스 URL 수정

```http
PATCH /api/blog-reference-urls/{referenceUrlId}
```

요청 body는 부분 수정 방식입니다. 바꾸고 싶은 필드만 보내면 됩니다.

```json
{
  "description": "Spring Boot 3.x 설정 예시를 설명할 때만 참고",
  "tags": ["Spring Boot", "3.x", "공식문서"],
  "active": false
}
```

### 레퍼런스 URL 삭제

```http
DELETE /api/blog-reference-urls/{referenceUrlId}
```

삭제하면 이후 AI 작업에서 더 이상 참고하지 않습니다. 단순히 잠시 빼고 싶다면 삭제보다 `active=false` 수정을 권장합니다.
