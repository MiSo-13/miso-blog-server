# 배포 가이드

이 프로젝트는 `C:\pjt\magi-platform`의 배포 방식을 참고하되, 단일 서버 구조에 맞춰 더 단순하게 구성했습니다.

## 기본 포트

MAGI 서버와 충돌하지 않도록 기본 서버 포트를 분리했습니다.

| 항목 | 기본값 | 설명 |
| --- | --- | --- |
| Miso Blog Server | `8010` | 외부 접근 API 포트 |

현재 AI job은 Spring `@Async`로 서버 내부에서 실행하므로 RabbitMQ 컨테이너는 배포 구성에서 제외했습니다.

## Private 설정 파일

MAGI와 같은 방식으로 Jenkins 서버의 private 파일을 빌드 전에 프로젝트로 복사합니다.

| 항목 | 경로 |
| --- | --- |
| Jenkins 서버 원본 | `/miso-blog/private/application-private.yml` |
| 빌드 워크스페이스 대상 | `src/main/resources/application-private.yml` |

Jenkinsfile은 배포 시작 시 원본 파일 존재 여부를 확인하고, 빌드 전에 대상 경로로 복사합니다. 배포가 끝나면 워크스페이스에 복사된 private 파일을 삭제합니다.

`application-private.yml`에는 DB, OpenAI, GitHub 같은 민감 설정을 넣습니다.

```yaml
db:
  url: jdbc:mysql://host.docker.internal:3306/miso-blog?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&serverTimezone=Asia/Seoul
  username: root
  password: change-me

openai:
  api-key:
  admin-key:
  model: gpt-4.1-mini
  budget-limit-usd:

github:
  token:
  owner:
```

서버에 MySQL이 Docker 밖에서 실행 중이면 `host.docker.internal`을 사용합니다. MySQL도 Docker 네트워크 안에 있다면 해당 서비스명으로 바꾸면 됩니다.

## 배포 환경 파일

`deploy.env.example`은 포트, 업로드 경로, JVM 옵션처럼 민감하지 않은 배포 옵션만 담습니다. `.env.deploy`은 git에 올라가지 않습니다.

```powershell
Copy-Item deploy.env.example .env.deploy
```

## Docker Compose 배포

```powershell
docker compose --env-file .env.deploy -f docker-compose.deploy.yml up -d --build
```

확인:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8010/api/system/health
```

포트를 바꾸고 싶으면 `.env.deploy`의 `MISO_BLOG_PORT`를 변경합니다.

`deploy.env.example`의 업로드 저장소 경로는 Windows 로컬 배포 기준으로 `C:/pjt/miso-blog-server/uploads`를 사용합니다. Linux 서버에 올릴 때는 `MISO_BLOG_UPLOAD_ROOT=/srv/miso-blog/uploads`처럼 서버 경로로 바꾸면 됩니다.

## 로컬 Git 저장소 분석

Docker 컨테이너 안에서는 Windows의 `C:\pjt\...` 경로를 그대로 읽을 수 없습니다. `docker-compose.deploy.yml`은 기본적으로 host의 `C:/pjt`를 컨테이너의 `/data/local-projects`에 읽기 전용으로 mount합니다.

Docker 배포에서 로컬 repo 후보를 쓰려면 private 설정의 경로를 컨테이너 기준으로 적습니다.

```yaml
blog:
  local-repositories:
    defaults:
      - name: magi-platform
        local-path: /data/local-projects/magi-platform
        default-branch: main
        description: MAGI 참고 프로젝트
        active: true
```

로컬 개발에서만 실행할 때는 기존처럼 `C:\pjt\magi-platform` 경로를 사용할 수 있습니다.

## Jenkins 배포

`Jenkinsfile`은 MAGI와 비슷하게 private 설정 파일을 복사한 뒤 `clean build`를 수행하고, `.env.deploy`을 임시로 생성해서 Docker Compose로 배포합니다.

- 선택: `MISO_BLOG_PORT`, `BLOG_PUBLIC_BASE_URL`

민감값은 repository나 `.env.deploy`에 커밋하지 말고 `/miso-blog/private/application-private.yml`에서만 관리합니다.
