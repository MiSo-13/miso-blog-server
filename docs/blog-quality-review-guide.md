# 블로그 품질 리뷰 가이드

일반 블로그와 개발 블로그 초안을 바로 발행하지 않고, 프론트에서 먼저 품질 점수와 수정 포인트를 보여주기 위한 API입니다.

이 리뷰는 다음 관점으로 글을 검사합니다.

- AI가 쓴 것처럼 보이는 일반론, 과장, 반복 표현
- 입력 메모나 Git 분석 근거 없이 단정한 문장
- 읽기 흐름과 검색 유입 준비도
- 장기 수익화를 위한 링크, 이미지, 독자 행동 유도 가능성

## API

```http
POST /api/blog-posts/{blogPostId}/quality-review/ai
```

요청 body는 선택값입니다.

```json
{
  "originalInputMemo": "사용자가 처음 입력한 메모나 개발 글 생성 근거",
  "targetReader": "성수동 데이트 맛집을 찾는 20~30대 독자",
  "monetizationGoal": "검색 유입과 장기 애드센스 수익화"
}
```

응답 예시:

```json
{
  "blogPostId": 6,
  "verdict": "대체로 자연스럽지만 일부 단정 표현 보완 필요",
  "humanNaturalnessScore": 85,
  "factualGroundingScore": 70,
  "readabilityScore": 90,
  "seoReadinessScore": 75,
  "monetizationReadinessScore": 60,
  "publishReady": false,
  "strengths": ["개인 경험 위주로 작성되어 신뢰감이 있음"],
  "issues": ["예약 추천 문장이 구체적 근거 없이 단정적으로 표현됨"],
  "unsupportedClaims": ["예약하지 않고 방문하면 자리를 잡기 어려울 것 같다는 생각이 들었습니다."],
  "aiLikePhrases": ["음식 만족도가 높아 성수동 데이트 맛집으로 손색없었습니다."],
  "monetizationSuggestions": ["지도 링크와 관련 글 내부 링크 추가"],
  "revisionInstruction": "예약 관련 문장은 개인 경험임을 명확히 하고 단정 표현을 완화하세요.",
  "modelName": "gpt-4.1-mini"
}
```

## 프론트 연동 흐름

1. AI 글 생성 또는 Git 분석 결과를 Markdown 미리보기로 보여줍니다.
2. `quality-review/ai`를 호출해 점수, 문제 문장, 수익화 제안을 표시합니다.
3. `publishReady=false`이면 발행 버튼을 비활성화하거나 경고를 표시합니다.
4. 사용자가 원하면 `revisionInstruction`을 `POST /api/blog-posts/{blogPostId}/revise/ai`의 `revisionInstruction`에 넣어 재작성합니다.
5. 재작성 후 다시 품질 리뷰를 실행해 `publishReady=true` 또는 사용자의 수동 승인까지 반복합니다.

## 실제 테스트 결과

2026-05-03 기준으로 `gpt-4.1-mini`를 사용해 실제 호출을 검증했습니다.

- 일반 맛집 글: 자연스러움 85점, 근거 충실도 70점, 발행 가능 여부 `false`
- 개발 글: 자연스러움 55점, 근거 충실도 40점, 발행 가능 여부 `false`
- 품질 자동 개선 job: 임시 예약 링크와 가짜 전화번호는 제거했지만, 예약 필요성처럼 근거가 부족한 문장이 남아 `criteriaPassed=false`로 차단

현재 생성 결과는 초안으로는 쓸 수 있지만, 사람 검토 없이 바로 업로드할 수준은 아닙니다. 그래서 프론트에서는 품질 리뷰 결과를 사용자가 확인하고, 추가 수정 요청을 거쳐 발행하도록 설계하는 것이 안전합니다.

## 품질 자동 개선 API

품질 리뷰 결과의 `revisionInstruction`을 사람이 복사하지 않아도 되도록, 서버가 리뷰와 재작성을 반복하는 API입니다. 실제 OpenAI 호출이 여러 번 이어질 수 있으므로 프론트에서는 비동기 job API를 기본으로 사용하세요.

```http
POST /api/ai-jobs/blog-posts/{blogPostId}/quality-improve/ai
```

동기 호출이 필요한 내부 도구에서는 아래 API도 사용할 수 있습니다.

```http
POST /api/blog-posts/{blogPostId}/quality-improve/ai
```

요청 예시:

```json
{
  "reviewRequest": {
    "originalInputMemo": "주말 저녁 방문. 내부는 조용했고 따뜻한 조명이라 데이트하기 좋았음.",
    "targetReader": "성수동 데이트 맛집을 찾는 20~30대 독자",
    "monetizationGoal": "검색 유입과 장기 애드센스 수익화"
  },
  "maxRevisionRounds": 2,
  "minimumHumanNaturalnessScore": 85,
  "minimumFactualGroundingScore": 85,
  "minimumReadabilityScore": 80,
  "minimumSeoReadinessScore": 70,
  "minimumMonetizationReadinessScore": 55,
  "additionalRevisionMemo": "가격, 메뉴, 예약 여부는 사용자가 제공한 내용 안에서만 다뤄줘.",
  "tone": "사람이 직접 다듬은 듯한 담백한 후기체",
  "targetLength": "LONG",
  "preserveTitle": false,
  "preserveTags": true,
  "requirePublishReady": false,
  "markReviewReadyWhenPassed": true
}
```

응답은 최초 리뷰, 최종 리뷰, 최종 글, 실제 사용한 수정 지시문 목록을 함께 반환합니다. `criteriaPassed=true`이면 지정한 점수 기준을 통과했고, 근거 없는 주장이나 placeholder 링크/전화번호 같은 하드 블로커가 없다는 뜻입니다. `publishReady=true`이면 AI 리뷰어가 사람 검토 없이 발행해도 된다고 판단한 상태입니다.
