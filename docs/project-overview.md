# 프로젝트 설명

## 목표

Miso Blog Server는 사용자의 Git 저장소에서 실제 구현 기록을 읽고, 코드 변경과 commit 맥락을 바탕으로 개발 블로그 글감과 초안을 생성하는 서버입니다.

핵심은 “일반적인 기술 글”이 아니라 “내가 실제로 구현한 코드와 문제 해결 기록을 최대한 구체적으로 블로그화하는 것”입니다.

## 기본 분석 전략

보안을 위해 기본값은 로컬 분석입니다.

- `LOCAL_ONLY`: 로컬에 clone된 repo를 직접 읽고, 외부 AI 전송 없이 글감 후보와 Markdown 초안을 만듭니다.
- `OPENAI`: 사용자가 명시적으로 선택한 경우에만 source summary를 OpenAI로 전송해 고품질 초안을 생성합니다.
- 향후 `LOCAL_LLM`: Ollama 또는 LM Studio 같은 로컬 LLM으로 초안을 생성하는 모드를 추가할 예정입니다.

## 핵심 시나리오

1. 사용자가 로컬 Git 저장소 경로를 등록합니다.
2. 서버가 `git log`, `git show`, `git diff`로 최근 구현 기록과 변경 파일을 수집합니다.
3. 기본 `LOCAL_ONLY` 모드에서는 로컬에서 source summary, 키워드, 글감 후보, Markdown 초안을 만듭니다.
4. 사용자가 원하면 `OPENAI` 모드로 더 풍부한 초안을 생성합니다.
5. 사용자가 키워드와 글감 후보를 선택합니다.
6. 선택한 키워드와 작성 초점을 바탕으로 좋은 블로그 초안을 생성합니다.
7. 분석 결과를 블로그 초안으로 전환합니다.
8. 승인된 글을 GitHub Pages에 발행합니다.
9. 필요하면 Velog에 노출용으로 재발행합니다.

## Private Repo 처리 원칙

- GitHub token과 OpenAI key는 `application-private.yml` 또는 환경 변수에만 둡니다.
- 로컬 분석 기본값은 외부 전송이 없습니다.
- `OPENAI` 모드와 GitHub API 분석은 commit message와 patch 요약이 외부 API로 전송될 수 있습니다.
- 서버 DB에는 분석 근거인 source summary와 분석 결과를 저장합니다.
- source summary는 DB 저장 전과 OpenAI 전송 전에 secret masking 필터를 거칩니다.
- OpenAI key, GitHub token, Authorization header, password/secret/token 계열 설정값, private key block, JDBC URL 비밀번호는 `[MASKED]`로 치환합니다.
- 마스킹은 방어 장치이며, `application-private.yml`처럼 민감한 파일은 git에 commit하지 않는 운영 규칙을 유지해야 합니다.

## 발행 전략

- GitHub Pages: 원본 발행 채널입니다. 서버가 보관한 Markdown을 GitHub Pages 저장소의 `_posts` 경로에 commit하는 방식을 목표로 합니다.
- 자체 도메인: GitHub Pages에 연결된 custom domain을 기본 공개 주소로 사용합니다.
- Velog: 개발자 독자층 노출을 위한 보조 채널입니다. 서버의 Markdown 원본을 기반으로 재발행하거나 수동 업로드할 수 있게 설계합니다.

서버 DB의 `blog_posts`와 `blog_post_versions`가 글의 원본이며, 외부 채널은 발행 결과물로 취급합니다.

## 주요 도메인

- `LocalRepository`: 로컬에 clone된 Git 저장소
- `LocalRepositoryAnalysisReport`: 로컬 Git 분석 결과
- `GitRepository`: GitHub API 기반 분석 대상 저장소
- `GitAnalysisReport`: GitHub API 기반 AI 분석 결과
- `BlogPost`: 서버가 보관하는 Markdown 글 원본
- `BlogPostVersion`: 글 수정, 승인, 발행 상태 이력
- `PublishTarget`: GitHub Pages, Velog 같은 발행 대상
- `AiUsageLog`: AI 호출별 token 사용량과 예상 비용 이력

## 현재 API 범위

- 로컬 Git 저장소 등록/수정/조회
- 로컬 Git 커밋/변경사항 기반 LOCAL_ONLY 분석
- 선택적 OpenAI 분석
- 선택 키워드/글감 후보 기반 블로그 초안 작성
- GitHub 저장소 등록/수정/조회
- GitHub 최근 commit 기반 OpenAI 분석
- 키워드/글감 후보/Markdown 초안 저장
- 분석 결과를 블로그 초안으로 전환
- 블로그 글 검수/승인/발행 상태 관리
- GitHub Pages/Velog 발행 대상 관리
- OpenAI 비용/사용량 조회
- 분석 source summary secret masking

## 향후 작업

- Ollama 또는 LM Studio 기반 `LOCAL_LLM` 분석 모드
- GitHub issue, PR, Actions 실패 로그 수집
- AI 작업 큐와 재시도
- GitHub Pages 저장소에 Markdown 파일 commit
- Velog 노출용 export 또는 발행 보조
