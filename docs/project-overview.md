# 프로젝트 설명

## 목표

Miso Blog Server는 개발자의 Git 저장소에서 기술 주제와 문제 해결 기록을 찾아 AI 블로그 글로 정리하는 서버입니다. 초기 버전은 글 자동 발행보다 안전한 초안 생성과 검수 흐름에 집중합니다.

## 핵심 시나리오

1. 사용자가 Git 저장소를 등록합니다.
2. 서버가 commit diff, pull request, issue, 장애 메모 같은 분석 대상을 수집합니다.
3. AI 작업을 큐에 넣고 비동기로 분석합니다.
4. OpenAI가 기술 주제, 장애 원인, 해결 과정, 재발 방지 포인트를 구조화합니다.
5. 서버가 Markdown 블로그 초안을 저장합니다.
6. 사용자가 프론트에서 초안을 검수하고 승인합니다.
7. 승인된 글을 자체 블로그 또는 외부 발행 대상으로 업로드합니다.

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
  publish     블로그 발행 대상 연동
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
- `[ADD]`, `[MODIFY]`, `[FIX]` 커밋 메시지 스타일

## 향후 주요 도메인

- `GitRepository`: 분석할 Git 저장소
- `AnalysisSource`: commit, PR, issue, 장애 메모 같은 원천 데이터
- `AiJob`: AI 분석/작성 작업
- `BlogPost`: 생성된 블로그 초안
- `BlogPostVersion`: 초안 수정 이력
- `PublishTarget`: 자체 블로그, GitHub Pages, 외부 블로그 같은 발행 대상
- `PublishAttempt`: 업로드 시도와 실패 이력
