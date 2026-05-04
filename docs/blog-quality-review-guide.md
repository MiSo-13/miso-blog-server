# 블로그 품질 리뷰 가이드

일반 블로그와 개발 블로그 초안을 바로 발행하지 않고, 프론트에서 먼저 품질 점수와 수정 포인트를 보여주기 위한 API입니다.

이 리뷰는 다음 관점으로 글을 검사합니다.

- AI가 쓴 것처럼 보이는 일반론, 과장, 반복 표현
- 입력 메모나 Git 분석 근거 없이 단정한 문장
- 읽기 흐름과 검색 유입 준비도
- 일반 블로그의 네이버 블로그 제목, 도입부, 문단 길이, 사진 설명, 키워드 자연스러움
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
  "referenceFeedback": ["저장된 레퍼런스는 공간 분위기를 먼저 묘사한 뒤 메뉴 경험으로 넘어가는데, 현재 글은 메뉴 평가가 먼저 나와 흐름이 조금 급합니다."],
  "referenceSentenceSuggestions": ["레퍼런스의 긴 문장을 그대로 복사하지 말고, '처음 들어갔을 때 느껴진 분위기'처럼 관찰 지점을 먼저 여는 구조를 참고하세요."],
  "naverBlogFeedback": ["제목에 장소와 핵심 경험은 들어가지만 '맛집' 키워드가 반복되어 조금 광고처럼 보입니다.", "도입부가 짧아 검색 유입 독자가 방문 맥락을 이해하기 어렵습니다."],
  "naverBlogTitleSuggestions": ["성수동 파스타 후기, 조용한 분위기와 트러플 크림 파스타", "성수 데이트 맛집으로 다녀온 파스타집 솔직 후기"],
  "naverBlogStructureSuggestions": ["도입부 다음에 공간 분위기 소제목을 먼저 두고, 메뉴 후기는 사진과 함께 배치하세요.", "마무리에는 추천 대상과 직접 확인이 필요한 정보를 분리하세요."],
  "naverTrendFeedback": ["상위 노출 글들은 도입부에서 방문 목적과 분위기를 먼저 설명하는데, 현재 글은 메뉴 장점으로 바로 들어가 검색 의도 대응이 약합니다."],
  "naverTrendTitlePatterns": ["장소 + 핵심 메뉴 + 분위기를 한 번에 담되, '맛집'을 반복하지 않는 제목이 많습니다."],
  "naverTrendStructurePatterns": ["공간 분위기, 대표 메뉴 사진, 아쉬운 점, 추천 대상 순서로 정보가 배치되는 흐름을 참고하세요."],
  "revisionInstruction": "예약 관련 문장은 개인 경험임을 명확히 하고 단정 표현을 완화하세요.",
  "modelName": "gpt-4.1-mini"
}
```

저장된 레퍼런스 URL이 있으면 서버가 AI 호출 직전에 해당 URL을 요청해 페이지 제목, 메타 설명, 핵심 문단 발췌를 프롬프트에 포함합니다.
접속 실패나 HTML이 아닌 응답은 실패 사유만 전달하고, AI가 본문을 읽은 것처럼 꾸미지 않도록 제한합니다.

일반 블로그는 네이버 블로그 게시를 기본 전제로 리뷰합니다. `naverBlogFeedback`, `naverBlogTitleSuggestions`, `naverBlogStructureSuggestions`는 프론트에서 별도 카드로 보여주면 좋고, 개발 블로그처럼 네이버 후기 기준이 핵심이 아닌 글에서는 빈 배열일 수 있습니다.

`NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`이 설정되어 있으면 서버는 품질 리뷰 시점에 네이버 블로그 검색 상위 글을 함께 참고합니다. 결과는 `naverTrendFeedback`, `naverTrendTitlePatterns`, `naverTrendStructurePatterns`에 반영됩니다. 이 값은 조회수 기반 인기글 랭킹이 아니라 네이버 블로그 검색 API의 정확도순 또는 날짜순 상위 결과를 사용한 근사 피드백입니다.

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
