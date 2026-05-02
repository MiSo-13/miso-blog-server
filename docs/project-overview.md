# 프로젝트 설명

## 목표

Miso Blog Server는 사용자의 private GitHub 저장소에서 실제 구현 기록을 읽고, 코드 변경과 commit 맥락을 바탕으로 개발 블로그 글감과 초안을 생성하는 서버입니다.

핵심은 “일반적인 기술 글”이 아니라 “내가 실제로 구현한 코드와 문제 해결 기록을 최대한 구체적으로 블로그화하는 것”입니다.

## 핵심 시나리오

1. 사용자가 private GitHub 저장소를 등록합니다.
2. 서버가 최근 commit message와 파일 patch를 수집합니다.
3. OpenAI가 구현 기능, 설계 판단, 트러블슈팅 포인트, 키워드를 분석합니다.
4. 서버가 글감 후보 여러 개와 추천 Markdown 초안을 저장합니다.
5. 사용자가 후보를 검수하고 블로그 초안으로 전환합니다.
6. 승인된 글을 GitHub Pages에 발행합니다.
7. 필요하면 Velog에 노출용으로 재발행합니다.

## Private Repo 처리 원칙

- GitHub token은 `application-private.yml` 또는 환경 변수에만 둡니다.
- commit patch는 블로그 분석을 위해 OpenAI API로 전송됩니다.
- 서버 DB에는 분석 근거인 source summary와 AI 응답을 저장합니다.
- 민감 정보가 코드에 포함될 가능성이 있으면, 추후 secret masking 필터를 추가해야 합니다.

## 발행 전략

- GitHub Pages: 원본 발행 채널입니다. 서버가 보관한 Markdown을 GitHub Pages 저장소의 `_posts` 경로에 commit하는 방식을 목표로 합니다.
- 자체 도메인: GitHub Pages에 연결된 custom domain을 기본 공개 주소로 사용합니다.
- Velog: 개발자 독자층 노출을 위한 보조 채널입니다. 서버의 Markdown 원본을 기반으로 재발행하거나 수동 업로드할 수 있게 설계합니다.

서버 DB의 `blog_posts`와 `blog_post_versions`가 글의 원본이며, 외부 채널은 발행 결과물로 취급합니다.

## 주요 도메인

- `GitRepository`: 분석할 GitHub 저장소
- `GitAnalysisReport`: 최근 commit 기반 AI 분석 결과
- `BlogPost`: 서버가 보관하는 Markdown 글 원본
- `BlogPostVersion`: 글 수정, 승인, 발행 상태 이력
- `PublishTarget`: GitHub Pages, Velog 같은 발행 대상
- `AiUsageLog`: AI 호출별 token 사용량과 예상 비용 이력

## 현재 API 범위

- GitHub 저장소 등록/수정/조회
- 최근 commit 기반 AI 분석
- 키워드/글감 후보/Markdown 초안 저장
- 분석 결과를 블로그 초안으로 전환
- 블로그 글 검수/승인/발행 상태 관리
- GitHub Pages/Velog 발행 대상 관리
- OpenAI 비용/사용량 조회

## 향후 작업

- secret masking 필터
- GitHub issue, PR, Actions 실패 로그 수집
- AI 작업 큐와 재시도
- GitHub Pages 저장소에 Markdown 파일 commit
- Velog 노출용 export 또는 발행 보조
