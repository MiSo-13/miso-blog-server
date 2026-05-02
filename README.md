# Miso Blog Server

Miso Blog Server는 Git 저장소의 코드 변경, 기술 이슈, 장애 해결 과정을 수집하고 OpenAI를 이용해 블로그 초안을 생성하는 서버입니다.

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
- Blog drafts: `POST /api/blog-posts/draft/manual`
- Blog posts: `GET /api/blog-posts`
- Blog post detail: `GET /api/blog-posts/{blogPostId}`
- Publish strategy: `GET /api/publish-targets/strategy`
- Default publish targets: `POST /api/publish-targets/defaults`

## 문서

- [프로젝트 설명](docs/project-overview.md)
- [설정 가이드](docs/configuration-guide.md)
- [프론트 연동 가이드](docs/frontend-api-guide.md)
