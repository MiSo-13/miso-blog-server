# Miso Blog 사용자 가이드

이 문서는 프론트 화면에서 사용자 안내를 만들기 위한 서비스 사용 설명입니다. API 명세만 나열하기보다 사용자가 어떤 순서로 무엇을 입력하고, 결과를 어떻게 확인하고, 언제 수정/승인/발행하면 좋은지를 설명합니다.

## 서비스 개요

Miso Blog는 두 가지 글 작성 흐름을 지원합니다.

1. 개발 블로그 작성
   - 내 로컬 Git 저장소나 GitHub 저장소를 분석합니다.
   - 구현한 기능, 트러블슈팅, 장애 대응, 구조 개선, 성능 개선 같은 글감을 뽑습니다.
   - 분석 결과에서 키워드와 주제를 선택해 블로그 초안을 만듭니다.

2. 일반 블로그 작성
   - 맛집, 식당, 카페, 여행, 제품 리뷰, 일상 글을 작성합니다.
   - 사진, 메모, 꼭 넣을 문구, 키워드, 말투를 입력하면 AI가 자연스러운 초안을 만듭니다.

두 흐름 모두 최종적으로는 같은 `BlogPost` 초안으로 저장됩니다. 사용자는 초안을 직접 편집하고, AI에게 추가 수정 요청을 하고, 수정 전후 diff를 확인한 뒤 승인 및 발행할 수 있습니다.

## 공통 글 상태

글은 다음 상태로 이동합니다.

```text
DRAFT -> REVIEW_READY -> APPROVED -> PUBLISHED
```

| 상태 | 의미 | 프론트 권장 UI |
| --- | --- | --- |
| `DRAFT` | 작성 중인 초안 | 편집 가능, AI 수정 가능, 검수 요청 버튼 노출 |
| `REVIEW_READY` | 사용자가 검수할 준비가 된 글 | 편집 가능, 승인 버튼 노출 |
| `APPROVED` | 발행해도 되는 글 | GitHub Pages 발행, Velog export 버튼 노출 |
| `PUBLISHED` | 발행 완료 | 일반 수정은 제한, 상세/버전/발행 정보 위주로 표시 |

발행 전까지는 `PATCH /api/blog-posts/{blogPostId}`로 직접 수정할 수 있습니다. 발행 후 수정은 별도 재발행 정책이 필요하므로 현재 기본 수정 API에서는 막습니다.

## 공통 편집 흐름

AI가 만든 글도 반드시 사용자가 확인하고 수정할 수 있어야 합니다.

권장 화면 흐름:

1. AI 분석 또는 AI 초안 생성
2. 결과 화면에서 제목, 요약, 태그, Markdown 본문 표시
3. 사용자가 직접 Markdown 편집
4. 저장 버튼 클릭
5. 저장 후 버전 생성
6. 수정 전후 diff 확인
7. 필요하면 AI 추가 수정 요청
8. 검수 대기 또는 승인

직접 저장 API:

```http
PATCH /api/blog-posts/{blogPostId}
```

요청 예시:

```json
{
  "title": "수정한 제목",
  "slug": "custom-slug",
  "summary": "수정한 요약",
  "contentMarkdown": "# 수정한 본문\n\n사용자가 다듬은 Markdown 내용",
  "tags": ["Spring Boot", "OpenAI", "개발블로그"],
  "sourceNote": "AI 생성 후 수동 수정"
}
```

버전 조회:

```http
GET /api/blog-posts/{blogPostId}/versions
GET /api/blog-posts/{blogPostId}/versions/diff
```

프론트에서는 diff 화면에서 `title`, `summary`, `contentMarkdown`, `tags` 변경을 나눠 보여주면 좋습니다. `INSERT`는 추가된 줄, `DELETE`는 삭제된 줄, `EQUAL`은 변경 없는 줄입니다.

## AI 작업 처리

시간이 오래 걸리는 AI 작업은 비동기 job API 사용을 권장합니다.

```http
POST /api/ai-jobs/blog-posts/draft/ai-general
POST /api/ai-jobs/git-repositories/{repositoryId}/analyze
POST /api/ai-jobs/blog-posts/{blogPostId}/revise/ai
POST /api/ai-jobs/blog-posts/{blogPostId}/quality-improve/ai
GET /api/ai-jobs/{jobId}
POST /api/ai-jobs/{jobId}/retry
```

프론트 권장 동작:

1. 작업 시작 API 호출
2. `jobId` 저장
3. 1~3초 간격으로 `GET /api/ai-jobs/{jobId}` polling
4. `PENDING`, `RUNNING`이면 로딩 표시
5. `SUCCEEDED`이면 `resultBlogPostId`로 글 상세 조회
6. `FAILED`이면 `failure.message`, `failure.actionGuide`를 표시
7. `retryable=true`이면 재시도 버튼 노출

실패 안내 예시:

- Rate limit: 잠시 후 다시 시도하거나 요청 길이를 줄여주세요.
- API key 오류: 관리자에게 OpenAI API key 설정을 확인해달라고 안내하세요.
- 입력값 오류: 필수 문구, 메모, 제목 힌트 등 사용자가 수정할 수 있는 항목을 강조하세요.

GitHub 저장소 분석은 GitHub API 조회와 OpenAI 분석이 이어지므로 1분 이상 걸릴 수 있습니다. 프록시나 다른 서버를 거쳐 호출하면 동기 API에서 504 Gateway Time-out이 날 수 있으니, 프론트에서는 `POST /api/ai-jobs/git-repositories/{repositoryId}/analyze`를 기본 분석 버튼으로 사용하세요.

## 개발 블로그 작성 가이드

개발 블로그 흐름은 “내가 실제로 구현한 코드”에서 글감을 최대한 많이 뽑아내는 것이 목표입니다.

### 추천 방식: 로컬 Git 분석

기본 추천은 로컬 Git 저장소 분석입니다. 서버가 로컬에 clone된 프로젝트를 직접 읽고, 기본값인 `LOCAL_ONLY` 모드에서는 외부 AI로 코드 내용을 보내지 않습니다.

사용자에게 보여줄 핵심 안내:

> 기본 분석은 내 PC 또는 서버에 있는 Git 기록만 읽고, 외부 AI로 코드를 보내지 않습니다. 더 풍부한 문장 생성이 필요할 때만 OpenAI 분석 또는 OpenAI 글 작성을 선택할 수 있습니다.

### 1. 로컬 저장소 후보 불러오기

관리자가 `application-private.yml`에 자주 쓰는 저장소 후보를 넣어두면 프론트에서 목록으로 보여줄 수 있습니다.

```http
GET /api/local-repositories/defaults
```

응답의 주요 필드:

| 필드 | 설명 |
| --- | --- |
| `name` | 화면에 보여줄 프로젝트 이름 |
| `localPath` | 설정 파일에 입력된 경로 |
| `normalizedLocalPath` | 서버가 인식한 실제 Git 루트 경로 |
| `readable` | 서버가 해당 경로를 Git 저장소로 읽을 수 있는지 |
| `registered` | 이미 등록된 저장소인지 |
| `message` | 읽기 실패 사유 또는 안내 문구 |

프론트 권장 UI:

- `readable=true`, `registered=false`: “등록” 버튼 노출
- `readable=true`, `registered=true`: “분석 시작” 버튼 노출
- `readable=false`: 등록 버튼 비활성화, `message` 표시

### 2. 로컬 저장소 등록

사용자가 후보를 선택하거나 직접 경로를 입력하면 저장소를 등록합니다.

주의할 점:

- 이 API의 `localPath`는 “브라우저를 띄운 사용자 PC 경로”가 아니라 “서버 프로세스가 직접 접근할 수 있는 경로”입니다.
- 로컬 개발에서 서버를 Windows에서 실행 중이면 `C:\pjt\magi-platform` 같은 경로를 사용할 수 있습니다.
- Docker 배포에서 서버가 컨테이너 안에서 실행 중이면 `C:\...` 경로는 보이지 않습니다. 이 경우 아래의 “GitHub 저장소 선택 후 Docker 내부 clone” 흐름을 사용하세요.
- `로컬 저장소 경로가 디렉터리가 아닙니다.` 오류는 서버 기준으로 해당 경로가 없거나 디렉터리가 아니라는 뜻입니다.

```http
POST /api/local-repositories
```

요청 예시:

```json
{
  "name": "magi-platform",
  "localPath": "C:\\pjt\\magi-platform",
  "defaultBranch": "main",
  "description": "개인 프로젝트",
  "active": true
}
```

화면 입력값:

| 입력 | 필수 | 안내 |
| --- | --- | --- |
| 프로젝트 이름 | 예 | 사용자가 알아볼 이름 |
| 로컬 경로 | 예 | 서버가 접근할 수 있는 Git 저장소 경로 |
| 기본 브랜치 | 아니오 | 비우면 `main` |
| 설명 | 아니오 | 프로젝트 메모 |
| 활성화 | 아니오 | 분석 대상 여부 |

### 2-1. GitHub 저장소 선택 후 Docker 내부 clone

Docker 배포 환경에서는 사용자의 PC 경로를 서버가 직접 읽을 수 없습니다. 대신 GitHub token으로 접근 가능한 저장소를 조회하고, 사용자가 선택한 저장소를 컨테이너 내부 clone 전용 경로에 내려받은 뒤 기존 로컬 분석 흐름을 사용합니다.

권장 화면 흐름:

1. GitHub 저장소 목록 조회
2. 사용자가 분석할 repository 선택
3. 선택한 repository의 branch 목록 조회
4. branch 선택
5. `clone` 실행
6. 응답으로 받은 `repositoryId`로 로컬 분석 실행

저장소 목록 조회:

```http
GET /api/local-repositories/github/repositories
```

응답은 발행 설정의 GitHub 저장소 선택 응답과 같은 형태입니다.

| 필드 | 설명 |
| --- | --- |
| `name` | 저장소 이름 |
| `fullName` | `owner/repo` 형태의 저장소 전체 이름 |
| `ownerLogin` | 소유자 또는 조직 |
| `defaultBranch` | 기본 브랜치 |
| `privateRepository` | private 저장소 여부 |
| `htmlUrl` | GitHub 웹 URL |
| `updatedAt` | 최근 수정 시간 |

브랜치 목록 조회:

```http
GET /api/local-repositories/github/branches?repositoryFullName=owner/repo
```

clone 및 로컬 분석 대상으로 등록:

```http
POST /api/local-repositories/github/clone
```

요청 예시:

```json
{
  "repositoryFullName": "owner/magi-platform",
  "branchName": "main",
  "name": "magi-platform",
  "description": "GitHub에서 clone한 개발 블로그 분석 대상",
  "refreshExisting": true
}
```

입력값 안내:

| 입력 | 필수 | 설명 |
| --- | --- | --- |
| `repositoryFullName` | 예 | `owner/repo` 형식 |
| `branchName` | 아니오 | 비우면 기본 clone 동작을 사용합니다. 프론트에서는 선택한 branch를 보내는 것을 권장합니다. |
| `name` | 아니오 | 화면에 표시할 이름. 비우면 repo 이름을 사용합니다. |
| `description` | 아니오 | 프로젝트 설명 |
| `refreshExisting` | 아니오 | 이미 clone되어 있으면 `fetch/pull`로 최신화할지 여부. 기본적으로 최신화합니다. |

clone 성공 후 응답은 일반 로컬 저장소 응답과 같습니다. 이후에는 아래 분석 API를 그대로 호출하면 됩니다.

```http
POST /api/local-repositories/{repositoryId}/analyze
```

Docker 기준 기본 clone 경로는 `/app/repositories`입니다. 변경하려면 `application-private.yml` 또는 환경변수에 설정합니다.

```yaml
blog:
  local-repositories:
    clone-base-dir: /app/repositories
```

```bash
BLOG_LOCAL_REPOSITORY_CLONE_BASE_DIR=/app/repositories
```

운영 안내:

- Docker 이미지에는 Git CLI가 포함되어 있어야 합니다. 현재 서버 Dockerfile은 runtime 이미지에 `git`을 설치합니다.
- private repository clone을 위해 `github.token`이 필요합니다.
- Fine-grained token을 쓴다면 선택 대상 repository에 Contents 읽기 권한이 필요합니다.
- clone된 코드는 컨테이너 파일 시스템에 저장됩니다. 컨테이너 재생성 후에도 유지하려면 `/app/repositories`를 Docker volume으로 연결하는 것을 권장합니다.

### 3. 로컬 저장소 분석

```http
POST /api/local-repositories/{repositoryId}/analyze
```

요청 예시:

```json
{
  "commitLimit": 20,
  "includeUncommittedChanges": true,
  "analysisMode": "LOCAL_ONLY",
  "focus": "내가 구현한 기능, 트러블슈팅, 장애 대응 포인트를 개발 블로그 글감으로 많이 뽑아줘",
  "createBlogPost": false
}
```

입력값 설명:

| 입력 | 권장값 | 설명 |
| --- | --- | --- |
| `commitLimit` | 10~20 | 최근 몇 개 commit을 볼지 |
| `includeUncommittedChanges` | true | 아직 commit하지 않은 변경도 포함할지 |
| `analysisMode` | `LOCAL_ONLY` | 외부 AI 전송 없이 로컬 분석 |
| `focus` | 구체적으로 작성 | 글감의 방향을 지정 |
| `createBlogPost` | false | 분석 결과 확인 후 글 작성 권장 |

`analysisMode` 선택 안내:

- `LOCAL_ONLY`: 코드가 외부로 나가지 않습니다. 기본값으로 추천합니다.
- `OPENAI`: 마스킹된 분석 근거가 OpenAI로 전송됩니다. 사용자가 명시적으로 동의한 경우에만 실행하세요.

프론트에서 `OPENAI` 선택 시 표시할 안내:

> 이 모드는 Git 변경 요약과 일부 diff 정보를 마스킹한 뒤 OpenAI로 전송합니다. 민감한 코드나 내부 정책이 포함될 수 있다면 LOCAL_ONLY를 사용하세요.

### 4. 분석 결과 화면

분석 결과에는 다음 정보를 보여주면 좋습니다.

| 항목 | 설명 | 권장 UI |
| --- | --- | --- |
| `analysisSummary` | 분석 요약 | 상단 요약 영역 |
| `keywords` | 기술 키워드 후보 | 선택 가능한 태그/칩 |
| `topicCandidates` | 글감 후보 | 카드 목록 |
| `recommendedTitle` | 추천 제목 | 제목 입력 기본값 |
| `draftMarkdown` | 1차 초안 | Markdown 미리보기 |
| `sourceSummary` | 분석 근거 | 접을 수 있는 상세 영역 |

글감 후보 카드에 보여줄 내용:

- 제목 후보
- 글의 관점
- 왜 글감으로 좋은지
- 관련 키워드
- 관련 파일 또는 commit 정보가 있으면 함께 표시

### 5. 키워드와 주제 선택 후 글 작성

분석 결과에서 사용자가 키워드와 주제를 고르면 실제 블로그 초안을 생성합니다.

```http
POST /api/local-repositories/analysis-reports/{reportId}/write-blog-post
```

요청 예시:

```json
{
  "selectedKeywords": ["Spring Boot", "Docker", "GitHub Pages"],
  "selectedTopicTitle": "Docker 배포에서 DB 연결 문제를 해결한 과정",
  "writingFocus": "문제가 발생한 원인, 확인 과정, 해결 방법을 순서대로 정리",
  "audience": "개인 프로젝트를 운영 환경에 배포하려는 개발자",
  "writingMode": "LOCAL_ONLY",
  "markReviewReady": true
}
```

입력값 안내:

| 입력 | 설명 |
| --- | --- |
| `selectedKeywords` | 사용자가 선택한 핵심 키워드 |
| `selectedTopicTitle` | 선택한 글감 제목 또는 사용자가 직접 수정한 제목 |
| `writingFocus` | 글의 방향. 예: 장애 원인 중심, 구현 순서 중심 |
| `audience` | 독자. 예: Spring Boot 초급자, 개인 프로젝트 운영자 |
| `writingMode` | `LOCAL_ONLY` 또는 `OPENAI` |
| `markReviewReady` | true면 생성 후 바로 검수 대기 상태 |

`writingMode=LOCAL_ONLY`는 빠르고 안전하지만 문장이 다소 건조할 수 있습니다. `OPENAI`는 더 자연스러운 글을 만들 수 있지만 분석 근거를 외부 AI로 보내므로 확인 UI가 필요합니다.

### 6. 개발 블로그 편집 팁

프론트 사용자 가이드에 넣으면 좋은 안내:

- 실제 에러 메시지나 증상을 한 문단 추가하면 글이 훨씬 자연스러워집니다.
- “왜 이 방식을 선택했는지”를 직접 보강하면 AI 티가 줄어듭니다.
- 코드 전체보다 핵심 설정, 핵심 메서드, 문제를 해결한 diff 중심으로 넣는 것이 좋습니다.
- 제목은 “무엇을 해결했는지”가 드러나게 바꾸면 검색 유입에 유리합니다.

좋은 제목 예시:

- `Docker 배포에서 MySQL Connection refused를 해결한 과정`
- `Spring Boot에서 GitHub Pages 자동 발행 기능을 붙이며 배운 점`
- `로컬 Git 분석으로 개발 블로그 글감을 자동 추출해보기`

피하면 좋은 제목:

- `개발 기록`
- `프로젝트 수정`
- `오류 해결`

### 7. GitHub 저장소 분석

GitHub API로 private repository를 읽는 흐름도 있습니다.

```http
POST /api/git-repositories
POST /api/git-repositories/{repositoryId}/analyze
```

다만 보안상 기본 흐름은 로컬 Git 분석을 권장합니다. GitHub 분석은 원격 repository의 commit과 파일 정보를 읽고 OpenAI 분석으로 이어질 수 있으므로, 사용자가 명확히 선택했을 때만 사용하세요.

## 일반 블로그 작성 가이드

일반 블로그는 맛집, 식당, 카페, 여행, 제품 리뷰, 일상 글처럼 Git 분석과 무관한 글을 작성하는 흐름입니다.

목표는 AI가 티 나지 않는 자연스러운 초안을 만들고, 사용자가 실제 경험과 표현을 조금 더해 최종 발행 가능한 글로 다듬는 것입니다.

### 1. 사진 업로드

사진이 있다면 먼저 업로드합니다.

```http
POST /api/media/images
Content-Type: multipart/form-data
```

Form data:

| 입력 | 필수 | 설명 |
| --- | --- | --- |
| `file` | 예 | jpg, png, webp, gif |
| `altText` | 아니오 | 이미지 대체 텍스트 |
| `note` | 아니오 | 글 작성에 참고할 사진 메모 |

응답의 `publicUrl`을 일반 블로그 작성 요청의 `photos[].url`에 넣습니다.

여러 장을 한꺼번에 올릴 때는 묶음 업로드 API를 사용합니다.

```http
POST /api/media/images/batch
Content-Type: multipart/form-data
```

Form data:

| 입력 | 필수 | 설명 |
| --- | --- | --- |
| `files` | 예 | 여러 이미지 파일 |
| `altTexts` | 아니오 | 파일 순서에 맞춘 사진 설명 |
| `notes` | 아니오 | 파일 순서에 맞춘 배치 메모 또는 참고 메모 |

응답에는 `uploadGroupId`와 업로드된 `assets`가 포함됩니다. 일반 블로그 작성 요청에 `photoGroupId`를 넣으면 이 사진 묶음 전체를 기반으로 글을 작성합니다.

```json
{
  "uploadGroupId": "7f9d1f4c-7f4f-4f43-95aa-1f9f2d3f9d11",
  "uploadedCount": 3,
  "assets": [
    {
      "id": 10,
      "publicUrl": "/media/2026/05/03/outside.jpg",
      "altText": "가게 외관",
      "note": "도입부 근처"
    }
  ]
}
```

묶음 조회:

```http
GET /api/media/images/groups?uploadGroupId=7f9d1f4c-7f4f-4f43-95aa-1f9f2d3f9d11
```

프론트 권장 UI:

- 업로드 후 썸네일 표시
- 사진별 설명 입력
- 사진별 “본문 어디에 넣을지” 메모 입력
- 여러 장 업로드 후 `uploadGroupId`를 글 작성 form에 유지
- 대표 사진 선택 기능은 추후 확장 가능

### 2. 일반 블로그 초안 생성

동기 API:

```http
POST /api/blog-posts/draft/ai-general
```

권장 비동기 API:

```http
POST /api/ai-jobs/blog-posts/draft/ai-general
```

요청 예시:

```json
{
  "category": "RESTAURANT",
  "titleHint": "성수동 파스타 맛집 방문 후기",
  "placeName": "성수 올리브테이블",
  "addressHint": "서울 성동구 성수동",
  "requiredPhrases": [
    "예약하고 방문하는 걸 추천합니다",
    "트러플 크림 파스타가 특히 좋았습니다"
  ],
  "memo": "주말 저녁 방문. 매장은 조용하고 데이트하기 좋은 분위기. 가격은 조금 있지만 만족도는 높았음. 직원 응대가 친절했고 음식 나오는 속도도 괜찮았음.",
  "keywords": ["성수 맛집", "성수 파스타", "데이트 맛집", "트러플"],
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
  "imagePlacementNotes": "사진 자리표시는 [사진: 설명] 형태로 본문 중간에 넣어줘",
  "tone": "친근하고 자연스러운 방문 후기",
  "audience": "성수동 데이트 맛집을 찾는 사람",
  "targetLength": "LONG",
  "markReviewReady": true
}
```

사진 자료는 세 가지 방식으로 넣을 수 있습니다.

| 입력 | 용도 |
| --- | --- |
| `photoGroupId` | 여러 장 업로드 묶음 전체를 사용 |
| `photoAssetIds` | 이미지 목록에서 선택한 특정 사진만 사용 |
| `photos` | 외부 이미지 URL이나 직접 작성한 사진 설명 사용 |

일반적인 화면에서는 `photoGroupId`를 추천합니다. 사용자가 업로드한 묶음 전체를 글 작성 자료로 넘길 수 있어 입력이 짧고, 서버가 사진 URL과 설명을 자동으로 정리합니다.

카테고리:

| 값 | 용도 |
| --- | --- |
| `RESTAURANT` | 맛집, 식당 |
| `CAFE` | 카페 |
| `TRAVEL` | 여행 |
| `PRODUCT_REVIEW` | 제품 리뷰 |
| `DAILY` | 일상 |
| `ETC` | 기타 |

길이:

| 값 | 권장 용도 |
| --- | --- |
| `SHORT` | 짧은 후기, SNS 보조 글 |
| `MEDIUM` | 일반적인 블로그 후기 |
| `LONG` | 검색 유입을 노리는 상세 후기 |

### 3. 입력값 작성 팁

AI 티가 덜 나는 글을 만들려면 `memo`가 가장 중요합니다.

좋은 메모 예시:

> 토요일 저녁 6시쯤 방문. 웨이팅은 10분 정도 있었고, 예약 손님이 먼저 들어가는 분위기였다. 내부는 생각보다 조용했고 테이블 간격이 좁지는 않았다. 트러플 크림 파스타는 향이 강한 편이고 소스가 넉넉했다. 가격은 저렴하지 않지만 데이트 장소로는 만족스러웠다.

아쉬운 메모 예시:

> 맛있었음. 분위기 좋음. 추천.

필수 문구는 반드시 들어가야 하는 문장입니다. 광고 문구, 협찬 고지, 개인적으로 꼭 넣고 싶은 표현이 있으면 여기에 넣습니다.

키워드는 검색 유입을 위한 힌트입니다. 너무 많이 넣기보다 4~8개 정도를 권장합니다.

### 4. 일반 블로그 결과 화면

AI 초안 생성 후 프론트에서 보여줄 정보:

- 제목
- 요약
- 태그
- Markdown 본문
- 사용된 사진 자리표시
- 상태
- 저장/수정 버튼
- AI 추가 수정 요청 입력창
- 품질 리뷰 버튼

사용자에게 안내할 문구:

> AI가 만든 초안은 바로 발행하기보다 실제 경험이 드러나도록 한두 문단을 직접 보강하는 것을 권장합니다. 가격, 웨이팅, 방문 시간, 재방문 의사처럼 본인만 알 수 있는 정보를 넣으면 훨씬 자연스럽습니다.

### 5. AI 추가 수정 요청

초안이 너무 딱딱하거나, 특정 내용을 더 강조하고 싶으면 추가 수정 요청을 보냅니다.

```http
POST /api/blog-posts/{blogPostId}/revise/ai
```

비동기 권장:

```http
POST /api/ai-jobs/blog-posts/{blogPostId}/revise/ai
```

요청 예시:

```json
{
  "revisionInstruction": "도입부를 더 자연스럽게 바꾸고, 실제 방문 후기 느낌이 나도록 웨이팅과 분위기 이야기를 앞쪽에 넣어줘. 과장된 표현은 줄여줘.",
  "additionalMemo": "주말 저녁이라 사람이 많았지만 시끄럽지는 않았고, 직원 응대가 친절했음.",
  "tone": "친근하고 담백한 후기",
  "targetLength": "LONG",
  "preserveTitle": false,
  "preserveTags": true,
  "markReviewReady": true
}
```

프론트 입력 가이드:

- “무엇을 바꿀지”를 구체적으로 쓰게 합니다.
- “어떤 표현을 피할지”도 받을 수 있게 합니다.
- `preserveTitle`, `preserveTags`는 체크박스로 제공하면 좋습니다.

### 6. 품질 리뷰와 자동 개선

품질 리뷰는 글이 얼마나 자연스럽고, 사실 근거가 있고, 수익화/SEO 준비가 되었는지 점검합니다.

```http
POST /api/blog-posts/{blogPostId}/quality-review/ai
```

요청 예시:

```json
{
  "originalInputMemo": "주말 저녁 성수동 파스타집 방문 후기",
  "targetReader": "성수동 데이트 맛집을 찾는 20~30대",
  "monetizationGoal": "검색 유입과 체류 시간을 높이는 맛집 후기"
}
```

자동 개선:

```http
POST /api/ai-jobs/blog-posts/{blogPostId}/quality-improve/ai
```

요청 예시:

```json
{
  "reviewRequest": {
    "originalInputMemo": "주말 저녁 성수동 파스타집 방문 후기",
    "targetReader": "성수동 데이트 맛집을 찾는 사람",
    "monetizationGoal": "검색 유입용 맛집 블로그"
  },
  "maxRevisionRounds": 2,
  "minimumHumanNaturalnessScore": 80,
  "minimumFactualGroundingScore": 75,
  "minimumReadabilityScore": 80,
  "minimumSeoReadinessScore": 70,
  "minimumMonetizationReadinessScore": 70,
  "additionalRevisionMemo": "AI 티 나는 과장 표현은 줄이고 실제 방문 후기처럼 다듬어줘",
  "tone": "자연스럽고 담백한 후기",
  "targetLength": "LONG",
  "preserveTitle": false,
  "preserveTags": true,
  "requirePublishReady": true,
  "markReviewReadyWhenPassed": true
}
```

프론트에서는 품질 점수를 막대나 점수 카드로 보여주고, 개선 전후 diff로 사용자가 변경 내용을 확인하게 하면 좋습니다.

## 발행 가이드

Miso Blog의 기본 발행 전략은 GitHub Pages를 원본 채널로, Velog를 노출 채널로 사용하는 것입니다.

### 1. 발행 대상 기본 생성

```http
POST /api/publish-targets/defaults
GET /api/publish-targets/strategy
```

프론트에서는 설정 화면 첫 진입 시 기본 대상이 없으면 “기본 발행 채널 만들기” 버튼을 보여주면 됩니다.

### 2. GitHub Pages 저장소 선택

```http
GET /api/publish-targets/github/repositories
GET /api/publish-targets/github/branches?repositoryFullName=owner/repo
PATCH /api/publish-targets/{targetId}
POST /api/publish-targets/{targetId}/test-github-pages
```

권장 화면 흐름:

1. GitHub 저장소 목록 조회
2. `githubPagesCandidate=true`인 저장소를 추천 표시
3. 저장소 선택 후 브랜치 목록 조회
4. `_posts` 같은 content root path 입력
5. 연결 테스트 실행
6. 성공 시 발행 버튼 활성화

연결 테스트는 실제 파일을 쓰지 않고 읽기 권한과 경로만 확인합니다.

### 3. 승인 후 GitHub Pages 발행

```http
POST /api/blog-posts/{blogPostId}/publish/github-pages
```

요청 예시:

```json
{
  "targetId": 1,
  "commitMessage": "Publish post: Docker 배포에서 DB 연결 문제를 해결한 과정"
}
```

발행은 `APPROVED` 상태에서만 허용하는 UI를 권장합니다.

### 4. Velog export

Velog는 자동 발행보다 복사/붙여넣기용 export로 사용합니다.

```http
POST /api/blog-posts/{blogPostId}/export/velog
```

요청 예시:

```json
{
  "targetId": 2,
  "canonicalUrl": "https://blog.example.com/2026/05/03/docker-db-connection.html",
  "includeCanonicalLink": true,
  "includeSourceNote": true
}
```

프론트에서는 title, markdown, tags 각각에 복사 버튼을 제공하면 좋습니다.

## 프론트 화면 구성 제안

### 개발 블로그 화면

권장 메뉴:

- 저장소 관리
- 분석 실행
- 분석 결과
- 글 작성
- 글 편집
- 발행

저장소 관리 화면:

- 로컬 저장소 후보 카드
- 직접 경로 입력
- 등록/수정/비활성화
- 읽기 실패 메시지 표시

분석 실행 화면:

- commit 개수 선택
- uncommitted 포함 여부 체크
- `LOCAL_ONLY` / `OPENAI` 선택
- focus 입력
- 보안 안내 표시

분석 결과 화면:

- 요약
- 키워드 선택
- 글감 후보 선택
- source summary 접기/펼치기
- “선택한 내용으로 글 작성” 버튼

### 일반 블로그 화면

권장 메뉴:

- 사진 업로드
- 글 정보 입력
- AI 초안 생성
- 편집/추가 요청
- 품질 리뷰
- 발행

입력 화면:

- 카테고리 선택
- 장소명/주소 힌트
- 꼭 넣을 문구
- 메모
- 키워드
- 사진 목록
- 말투
- 독자
- 길이

결과 화면:

- 제목/요약/태그
- Markdown 편집기
- 사진 자리표시 확인
- 추가 수정 요청
- 저장
- diff 보기
- 검수 대기

## 사용자 안내 문구 예시

개발 블로그 분석 전:

> 기본 LOCAL_ONLY 분석은 코드를 외부 AI로 보내지 않고 Git 기록을 서버 안에서만 읽습니다. OpenAI 분석을 선택하면 마스킹된 코드 요약이 외부 AI로 전송될 수 있습니다.

일반 블로그 생성 전:

> AI 초안은 입력한 메모와 사진 설명을 바탕으로 작성됩니다. 방문 시간, 분위기, 가격대, 재방문 의사처럼 직접 경험한 정보를 많이 넣을수록 자연스러운 글이 만들어집니다.

AI 수정 전:

> 추가 요청은 구체적일수록 좋습니다. “더 자연스럽게”보다 “도입부에 방문 시간과 웨이팅을 넣고, 과장 표현을 줄여줘”처럼 적어보세요.

발행 전:

> 발행 후에는 일반 수정 API로 바로 수정할 수 없습니다. 제목, 본문, 태그, 이미지 위치를 마지막으로 확인해주세요.

GitHub Pages 연결 실패:

> GitHub token 권한, 저장소명, 브랜치명, `_posts` 경로를 확인해주세요. Fine-grained token을 사용한다면 대상 저장소에 Contents read/write 권한이 필요합니다.

DB 또는 서버 설정 실패:

> Docker 배포에서는 DB 주소에 `localhost`를 사용하면 컨테이너 자기 자신을 바라봅니다. MySQL이 host에서 실행 중이면 `host.docker.internal`을 사용하세요.

## 운영상 주의사항

- public 배포 전에는 인증/권한 처리가 필요합니다. 현재 개발 단계에서는 API가 열려 있을 수 있습니다.
- 실제 OpenAI 호출은 비용이 발생합니다. 프론트에서는 긴 작업을 비동기 job으로 처리하고, 실패 시 재시도 가능 여부를 보여주세요.
- 개발 블로그의 `OPENAI` 모드는 코드 요약이 외부 AI로 전송될 수 있으므로 사용자의 명시적 확인이 필요합니다.
- GitHub Pages 실제 발행은 저장소에 commit을 생성합니다. 테스트 발행과 실제 발행을 UI에서 구분하면 좋습니다.
- Velog는 현재 export 방식입니다. 자동 발행은 추후 작업으로 남겨두는 것을 권장합니다.
