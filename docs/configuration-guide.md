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
  url: jdbc:mysql://localhost:3306/miso_blog?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&serverTimezone=Asia/Seoul
  username: root
  password: 1234

rabbitmq:
  host: localhost
  port: 5672
  username: guest
  password: guest

openai:
  api-key: 개인 OpenAI API Key
  model: gpt-4.1-mini

github:
  token: 개인 GitHub Token
```

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
| `OPENAI_MODEL` | OpenAI 모델 |
| `GITHUB_TOKEN` | GitHub API Token |

## 로컬 인프라

RabbitMQ는 Docker Compose로 실행합니다.

```bash
docker compose up -d rabbitmq
```

MySQL은 로컬 설치 또는 별도 컨테이너를 사용할 수 있습니다. 초기 DB 이름은 `miso_blog`를 기준으로 합니다.
