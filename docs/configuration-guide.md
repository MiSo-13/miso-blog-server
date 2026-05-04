# 설정 가이드

## Profile 구성

기본 profile은 `local`이며 `application-private.yml`을 함께 읽습니다. 이 파일은 git에 올라가지 않습니다.

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
    include: private
```

## application-private.yml

`src/main/resources/application-private.yml.example`을 복사해서 사용합니다.

```yaml
db:
  url: jdbc:mysql://localhost:3306/miso-blog?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&serverTimezone=Asia/Seoul
  username: root
  password: 1234

openai:
  api-key: 개인 OpenAI API Key
  admin-key: 조직 비용과 사용량 조회용 OpenAI Admin API Key
  model: gpt-4.1-mini
  budget-limit-usd: 월 예산 한도. 예: 20

github:
  token: 개인 GitHub Token
  owner: GitHub 계정명
  # 아래 값들은 직접 고정하고 싶을 때만 사용합니다. 기본 흐름은 웹에서 repo/branch를 선택하는 방식입니다.
  pages-repository-name:
  pages-repository-full-name:
  pages-branch: main
  pages-content-root-path: _posts
  pages-base-url:
  pages-custom-domain:
  analysis:
    max-all-commits: 300

blog:
  local-repositories:
    clone-base-dir: /app/repositories
    defaults:
      - name: magi-platform
        local-path: C:\pjt\magi-platform
        default-branch: main
        description: MAGI 참고 프로젝트
        active: true
```

GitHub Pages 발행을 사용하려면 `github.token`에 대상 저장소 `Contents: Read and write` 권한이 필요합니다. Fine-grained token을 권장하고, 대상 저장소만 선택하는 것이 안전합니다.

기본 흐름은 `github.token`, `github.owner`만 private에 입력하고, 프론트에서 저장소와 브랜치를 선택하는 방식입니다. 직접 고정하고 싶을 때만 `pages-repository-name` 또는 `pages-repository-full-name`을 입력하세요.

`MiSo-13/tech-blog`처럼 project Pages 저장소를 고정하려면 `pages-repository-full-name: MiSo-13/tech-blog`, `pages-branch: main`, `pages-content-root-path: _posts`를 사용합니다. `pages-base-url`을 비워두면 서버가 `https://miso-13.github.io/tech-blog`로 추론합니다. custom domain을 연결했다면 `pages-custom-domain` 또는 발행 대상의 `customDomain`을 설정하세요.

`github.analysis.max-all-commits`는 GitHub 저장소 분석에서 `analyzeAllCommits=true`를 사용할 때 최대 몇 개 commit까지 조회할지 정합니다. 값이 너무 크면 GitHub API 호출과 OpenAI 분석 시간이 길어질 수 있으므로 기본값 300에서 시작하는 것을 권장합니다.

로컬 Git 분석은 서버가 접근 가능한 로컬 경로를 직접 읽습니다. 자주 분석할 프로젝트는 `blog.local-repositories.defaults`에 후보로 적어두면 프론트에서 `GET /api/local-repositories/defaults`로 불러와 선택 UI를 만들 수 있습니다. 이 값도 개인 PC 경로라서 `application-private.yml`에서만 관리하는 것을 권장합니다.

Docker 배포에서는 사용자 PC의 `C:\...` 경로를 컨테이너가 직접 읽을 수 없습니다. GitHub 저장소를 웹에서 선택해 clone하는 흐름을 쓰려면 `blog.local-repositories.clone-base-dir` 또는 `BLOG_LOCAL_REPOSITORY_CLONE_BASE_DIR`을 컨테이너 내부 경로로 설정합니다. 기본 배포 compose는 `/data/miso-blog/repositories`를 사용하고 host volume에 보존합니다.

## 환경 변수

운영 환경에서는 private 파일 대신 환경 변수를 우선 사용할 수 있습니다.

| 이름 | 설명 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 실행 profile |
| `SERVER_PORT` | 서버 포트 |
| `DB_URL` | MySQL JDBC URL |
| `DB_USERNAME` | MySQL 계정 |
| `DB_PASSWORD` | MySQL 비밀번호 |
| `OPENAI_API_KEY` | OpenAI API Key |
| `OPENAI_ADMIN_KEY` | OpenAI Admin API Key |
| `OPENAI_MODEL` | OpenAI 모델 |
| `OPENAI_BUDGET_LIMIT_USD` | 월 예산 한도 USD |
| `GITHUB_TOKEN` | GitHub API Token |
| `GITHUB_OWNER` | GitHub 계정명 |
| `GITHUB_PAGES_REPOSITORY_NAME` | GitHub Pages 저장소명 |
| `GITHUB_PAGES_REPOSITORY_FULL_NAME` | `owner/repository` 형식 저장소명 |
| `GITHUB_PAGES_BRANCH` | GitHub Pages 브랜치 |
| `GITHUB_PAGES_CONTENT_ROOT_PATH` | 글 파일 저장 경로. 기본값 `_posts` |
| `GITHUB_PAGES_BASE_URL` | GitHub Pages 공개 URL |
| `GITHUB_PAGES_CUSTOM_DOMAIN` | 자체 도메인 |
| `GITHUB_ANALYSIS_MAX_ALL_COMMITS` | 전체 GitHub 분석 모드에서 조회할 최대 commit 수. 기본값 300 |
| `BLOG_LOCAL_REPOSITORY_CLONE_BASE_DIR` | GitHub에서 선택한 분석 대상 repo를 clone할 컨테이너 내부 경로 |
| `BLOG_MEDIA_UPLOAD_DIR` | 블로그 이미지 저장 경로 |
| `BLOG_MEDIA_PUBLIC_URL_PREFIX` | 이미지 public URL prefix |
| `BLOG_MEDIA_MAX_FILE_SIZE` | multipart 파일 크기 제한 |
| `BLOG_MEDIA_MAX_REQUEST_SIZE` | multipart 요청 전체 제한 |
| `BLOG_MEDIA_MAX_FILE_SIZE_BYTES` | 서비스 내부 파일 크기 제한 |

## 로컬 인프라

현재 필수 인프라는 MySQL입니다. AI 비동기 작업은 RabbitMQ 없이 Spring `@Async`로 서버 내부에서 처리합니다.

MySQL 초기 DB 이름은 `miso-blog`입니다.

```sql
CREATE DATABASE `miso-blog` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 블로그 이미지 저장소

기본 업로드 경로는 `uploads/blog-media`입니다. 이 경로는 git에 올라가지 않습니다.

로컬 실행 중 업로드된 이미지는 `/media/{yyyy}/{MM}/{dd}/{filename}` 형태로 접근할 수 있습니다.

## OpenAI Admin Key

OpenAI 사용량과 실제 비용 조회에는 일반 project key가 아니라 Admin API Key가 필요합니다.

- `openai.api-key`: 글 생성 등 일반 OpenAI 호출
- `openai.admin-key`: `/v1/organization/costs`, `/v1/organization/usage/completions` 조회

Admin Key가 없으면 비용/사용량 상세 API는 `BAD_REQUEST`를 반환합니다.
