# 설정 가이드

## Profile 구성

기본 profile은 `local`입니다.

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
    include: private
```

민감 정보는 `application-private.yml`에 작성합니다. 이 파일은 git에 올리지 않습니다.

## application-private.yml

`src/main/resources/application-private.yml.example`을 복사해서 사용합니다.

```yaml
db:
  url: jdbc:mysql://localhost:3306/miso-blog?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&serverTimezone=Asia/Seoul
  username: root
  password: 1234

rabbitmq:
  host: localhost
  port: 5672
  username: guest
  password: guest

openai:
  api-key: 개인 OpenAI API Key
  admin-key: 조직 비용과 사용량 조회용 OpenAI Admin API Key
  model: gpt-4.1-mini
  budget-limit-usd: 월 예산 한도. 예: 20

github:
  token: 개인 GitHub Token
```

GitHub Pages 발행을 사용하려면 `github.token`에 발행 저장소 contents write 권한이 필요합니다. classic PAT를 쓰는 경우 `repo` 권한, fine-grained token을 쓰는 경우 대상 저장소의 `Contents: Read and write` 권한을 부여합니다.

## 환경 변수

운영 환경에서는 private 파일 대신 환경 변수를 우선 사용할 수 있습니다.

| 이름 | 설명 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 실행 profile |
| `SERVER_PORT` | 서버 포트 |
| `DB_URL` | MySQL JDBC URL |
| `DB_USERNAME` | MySQL 계정 |
| `DB_PASSWORD` | MySQL 비밀번호 |
| `RABBITMQ_HOST` | RabbitMQ host |
| `RABBITMQ_PORT` | RabbitMQ port |
| `RABBITMQ_USERNAME` | RabbitMQ 계정 |
| `RABBITMQ_PASSWORD` | RabbitMQ 비밀번호 |
| `OPENAI_API_KEY` | OpenAI API Key |
| `OPENAI_ADMIN_KEY` | OpenAI Admin API Key |
| `OPENAI_MODEL` | OpenAI 모델 |
| `OPENAI_BUDGET_LIMIT_USD` | 월 예산 한도 USD |
| `GITHUB_TOKEN` | GitHub API Token. GitHub Pages 발행 시 contents write 권한 필요 |
| `BLOG_MEDIA_UPLOAD_DIR` | 블로그 이미지 저장 경로. 기본값 `uploads/blog-media` |
| `BLOG_MEDIA_PUBLIC_URL_PREFIX` | 이미지 public URL prefix. 기본값 `/media` |
| `BLOG_MEDIA_MAX_FILE_SIZE` | multipart 단일 파일 제한. 기본값 `10MB` |
| `BLOG_MEDIA_MAX_REQUEST_SIZE` | multipart 요청 전체 제한. 기본값 `30MB` |
| `BLOG_MEDIA_MAX_FILE_SIZE_BYTES` | 서비스 레벨 파일 크기 제한. 기본값 `10485760` |

## 로컬 인프라

RabbitMQ는 Docker Compose로 실행합니다.

```bash
docker compose up -d rabbitmq
```

MySQL은 로컬 설치 또는 별도 컨테이너를 사용할 수 있습니다. 초기 DB 이름은 `miso-blog`를 기준으로 합니다.

```sql
CREATE DATABASE `miso-blog` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 블로그 이미지 저장소

기본 업로드 경로는 `uploads/blog-media`입니다. 이 경로는 git에 올라가지 않도록 `.gitignore`에 등록되어 있습니다.

로컬 실행 시 업로드된 이미지는 `/media/{yyyy}/{MM}/{dd}/{filename}` 형태로 접근할 수 있습니다.

## OpenAI Admin Key

OpenAI 사용량과 실제 비용 조회는 일반 project key가 아니라 Admin API Key가 필요합니다.

- `openai.api-key`: 글 생성 등 일반 OpenAI 호출용입니다.
- `openai.admin-key`: `/v1/organization/costs`, `/v1/organization/usage/completions` 조회용입니다.

Admin Key가 없으면 `/api/admin/openai/summary`는 조회 불가 사유를 내려주고, costs/usage 상세 API는 `BAD_REQUEST`를 반환합니다.
