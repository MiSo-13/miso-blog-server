# 프로젝트 설명

## 목표

Miso Blog Server는 개발자의 Git 저장소에서 기술 주제와 문제 해결 기록을 찾아 AI 블로그 글로 정리하는 서버입니다. 초기 버전은 글 자동 발행보다 안전한 초안 생성과 검수 흐름에 집중합니다.

## 발행 전략

기본 발행 전략은 다음과 같습니다.

- GitHub Pages: 원본 발행 채널입니다. 서버가 보관한 Markdown을 GitHub Pages 저장소의 `_posts` 경로에 commit하는 방식을 목표로 합니다.
- 자체 도메인: GitHub Pages에 연결된 custom domain을 기본 공개 주소로 사용합니다.
- Velog: 개발자 독자층 노출을 위한 보조 채널입니다. 서버의 Markdown 원본을 기반으로 재발행하거나 수동 업로드할 수 있게 설계합니다.

서버 DB의 `blog_posts`와 `blog_post_versions`가 글의 원본이며, 외부 채널은 발행 결과물로 취급합니다.

## 핵심 시나리오

1. 사용자가 Git 저장소를 등록합니다.
2. 서버가 commit diff, pull request, issue, 장애 메모 같은 분석 대상을 수집합니다.
3. AI 작업을 큐에 넣고 비동기로 분석합니다.
4. OpenAI가 기술 주제, 장애 원인, 해결 과정, 재발 방지 포인트를 구조화합니다.
5. 서버가 Markdown 블로그 초안을 저장합니다.
6. 사용자가 프론트에서 초안을 검수하고 승인합니다.
7. 승인된 글을 GitHub Pages에 발행합니다.
8. 필요하면 Velog에 노출용으로 재발행합니다.

## 초기 아키텍처

현재는 단일 Spring Boot 모듈로 시작합니다. 패키지는 향후 `api`, `core`, `batch` 모듈로 나누기 쉽게 도메인 기준으로 배치합니다.

```text
com.miso.blog
  common      공통 응답, 예외, 설정
  system      서버 상태 확인
  repository  Git 저장소 등록과 동기화
  git         GitHub/Git 수집 클라이언트
  analysis    코드/이슈 분석 요청
  ai          OpenAI 호출과 프롬프트
  post        블로그 초안과 버전 관리
  publish     GitHub Pages와 Velog 발행 대상 관리
  job         AI 작업 큐와 재시도
  admin       운영 조회와 수동 재처리
```

## 참고한 MAGI 패턴

`C:\pjt\magi-platform`의 다음 패턴을 기준으로 서버를 확장합니다.

- Java 17, Spring Boot 3.5.10
- `application.yml` + `application-private.yml`
- `ApiDataResponse`, `ApiErrorResponse` 형태의 공통 응답
- `GeneralException`, `ErrorCode` 기반 예외 처리
- `AiJob`, `AiJobAttempt` 기반 AI 작업 추적
- RabbitMQ와 Outbox 기반 비동기 처리
- OpenAI Costs/Usage API 기반 운영 비용 조회
- `[ADD]`, `[MODIFY]`, `[FIX]` 커밋 메시지 스타일

## 주요 도메인

- `BlogPost`: 서버가 보관하는 Markdown 글 원본
- `BlogPostVersion`: 글 수정, 승인, 발행 상태 이력
- `PublishTarget`: GitHub Pages, Velog 같은 발행 대상
- `AiUsageLog`: AI 호출별 token 사용량과 예상 비용 이력

## 향후 주요 도메인

- `GitRepository`: 분석할 Git 저장소
- `AnalysisSource`: commit, PR, issue, 장애 메모 같은 원천 데이터
- `AiJob`: AI 분석/작성 작업
- `PublishAttempt`: GitHub Pages commit 또는 Velog 노출 시도 이력

## OpenAI 운영 기능

초기 운영 API는 다음 정보를 제공합니다.

- Admin API Key 설정 상태
- 오늘 사용 금액
- 이번 달 누적 사용 금액
- 월 예산 대비 남은 금액
- 일자별 실제 비용
- 모델/API key/project 기준 completion token 사용량
- 예상 input/output token 기반 호출 비용 추정

실제 청구 기준 비용은 OpenAI Costs API를 기준으로 보고, 글 생성 전 예상 비용은 서버 내부 가격표로 계산합니다.
