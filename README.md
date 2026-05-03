# Miso Blog Server

Miso Blog Server는 Git 저장소의 코드 변경, 기술 이슈, 장애 해결 과정을 수집하고 OpenAI를 이용해 블로그 초안을 생성하는 서버입니다.
사진 설명, 필수 문구, 메모, 키워드를 기반으로 맛집/카페/여행 같은 일반 블로그 초안도 생성할 수 있습니다.

## 기술 스택

- Java 17
- Spring Boot 3.5.10
- Gradle
- Spring Web / JPA / Validation / Security
- RabbitMQ
- MySQL
- OpenAI
- Springdoc OpenAPI

## 초기 실행 준비

### 1. 개인 설정 파일 생성

`src/main/resources/application-private.yml.example` 파일을 `application-private.yml`로 복사한 뒤 로컬 DB, RabbitMQ, OpenAI, GitHub 값을 입력합니다.

`application-private.yml`은 git에 올라가지 않도록 `.gitignore`에 등록되어 있습니다.

### 2. MySQL 데이터베이스 생성

```sql
CREATE DATABASE `miso-blog` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. RabbitMQ 실행

```bash
docker compose up -d rabbitmq
```

### 4. 서버 실행

```bash
./gradlew bootRun
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

## 주요 접속 경로

- Health Check: `http://localhost:8010/api/system/health`
- Swagger UI: `http://localhost:8010/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8010/v3/api-docs`
- RabbitMQ UI: `http://localhost:15672`

## OpenAI 운영 API

- Summary: `GET /api/admin/openai/summary`
- Costs: `GET /api/admin/openai/costs`
- Usage: `GET /api/admin/openai/usage/completions`
- Estimate: `GET /api/admin/openai/estimate`

## 블로그/발행 API

- Local repositories: `POST /api/local-repositories`
- Local analysis: `POST /api/local-repositories/{repositoryId}/analyze`
- Local write: `POST /api/local-repositories/analysis-reports/{reportId}/write-blog-post`
- Git repositories: `POST /api/git-repositories`
- Git analysis: `POST /api/git-repositories/{repositoryId}/analyze`
- Git reports: `GET /api/git-repositories/{repositoryId}/analysis-reports`
- Blog image upload: `POST /api/media/images`
- Blog drafts: `POST /api/blog-posts/draft/manual`
- General AI blog draft: `POST /api/blog-posts/draft/ai-general`
- AI blog revision: `POST /api/blog-posts/{blogPostId}/revise/ai`
- Blog posts: `GET /api/blog-posts`
- Blog post detail: `GET /api/blog-posts/{blogPostId}`
- Publish strategy: `GET /api/publish-targets/strategy`
- Default publish targets: `POST /api/publish-targets/defaults`
- GitHub Pages publish: `POST /api/blog-posts/{blogPostId}/publish/github-pages`
- Velog export: `POST /api/blog-posts/{blogPostId}/export/velog`

## 보안 메모

- 로컬 Git 분석은 기본적으로 `LOCAL_ONLY` 모드를 사용해 외부 AI 전송 없이 글감과 초안을 만듭니다.
- 분석 근거인 `sourceSummary`는 DB 저장 전과 OpenAI 전송 전에 secret masking 필터를 거칩니다.
- `application-private.yml`은 계속 git에 올리지 않고, 실제 키는 로컬 private 설정 또는 환경 변수에서만 관리합니다.
- GitHub Pages 발행용 GitHub token은 대상 저장소 contents write 권한이 필요합니다.
- 업로드 이미지는 기본적으로 `uploads/blog-media`에 저장되며 git에 올라가지 않습니다.

## 문서

- [프로젝트 설명](docs/project-overview.md)
- [설정 가이드](docs/configuration-guide.md)
- [프론트 연동 가이드](docs/frontend-api-guide.md)
