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

현재 생성 결과는 초안으로는 쓸 수 있지만, 사람 검토 없이 바로 업로드할 수준은 아닙니다. 그래서 프론트에서는 품질 리뷰 결과를 사용자가 확인하고, 추가 수정 요청을 거쳐 발행하도록 설계하는 것이 안전합니다.
