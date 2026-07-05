# RAG 골든셋 (Retrieval Hit@K / Faithfulness 평가용)

각 항목은 `## golden-번호: 카테고리` 헤더 아래에 `Q:`(질문), `Expected:`(기대 문서 id, 없으면 NONE), `Note:`(비고, 없으면 -) 세 줄이 이어지는 형식이다.

- `Expected: NONE`인 항목(hallucination trap)은 Hit@K 집계에서 제외하고, "검색 결과 없음/낮은 유사도로 필터링됨" 및 "모른다고 정직하게 답했는가"만 별도로 확인한다.
- `Note`가 있는 항목은 오답·환각을 유도하기 쉬운 케이스이므로 Faithfulness 채점 시 특히 주의 깊게 본다.

---

## golden-001: FAQ

Q: 매칭은 하루에 몇 번 가능해요?
Expected: faq-003
Note: -

## golden-002: FAQ

Q: 게시글 지우면 나중에 다시 살릴 수 있나요?
Expected: faq-007
Note: -

## golden-003: FAQ

Q: 이상한 사람을 신고하고 싶은데 어떻게 하나요?
Expected: faq-011
Note: -

## golden-004: FAQ

Q: 비밀번호를 잊어버렸는데 재설정할 수 있나요?
Expected: faq-013
Note: 답이 "찾기 기능 없음"이라 hallucination 유도 가능

## golden-005: FAQ

Q: 이 서비스 유료인가요?
Expected: faq-015
Note: -

## golden-006: POLICY-user

Q: 청소년도 가입할 수 있나요?
Expected: policy-user-003
Note: -

## golden-007: POLICY-user

Q: 다른 폰에서 로그인하면 기존 로그인은 어떻게 되나요?
Expected: policy-user-005
Note: -

## golden-008: POLICY-user

Q: 누군가를 차단하면 팔로우 관계는 어떻게 되나요?
Expected: policy-user-011
Note: -

## golden-009: POLICY-user

Q: 한 번 신고한 사람을 나중에 다시 신고할 수 있나요?
Expected: policy-user-014
Note: -

## golden-010: POLICY-match

Q: 매칭 상대에서 자동으로 제외되는 사람이 있나요?
Expected: policy-match-003
Note: -

## golden-011: TRAP

Q: 매칭할 때 위치나 성별로 필터링하면 실제로 적용되나요?
Expected: NONE
Note: 문서에 없는 기능

## golden-012: POLICY-match

Q: 매칭이 잡히면 바로 확정인가요, 수락해야 하나요?
Expected: policy-match-005
Note: -

## golden-013: POLICY-match

Q: 매칭 추천 이유는 볼 때마다 같은 내용인가요?
Expected: policy-match-008
Note: -

## golden-014: POLICY-feed

Q: 게시글에 사진이나 동영상은 몇 개까지 올릴 수 있나요?
Expected: policy-feed-003
Note: -

## golden-015: POLICY-feed

Q: 위치를 공개하면 정확한 주소가 보이나요?
Expected: policy-feed-005
Note: -

## golden-016: POLICY-feed

Q: 팔로워 공개로 설정한 글이 내 프로필 목록에서 남들에게 보이나요?
Expected: policy-feed-007
Note: -

## golden-017: POLICY-feed

Q: 지운 게시글은 언제까지 복구할 수 있나요?
Expected: policy-feed-011
Note: -

## golden-018: POLICY-chat

Q: 차단한 사람과 채팅방을 만들 수 있나요?
Expected: policy-chat-001
Note: -

## golden-019: POLICY-chat

Q: 그룹 채팅방은 최소 몇 명부터 만들 수 있나요?
Expected: policy-chat-004
Note: -

## golden-020: POLICY-chat

Q: 그룹 채팅 방장이 나가면 방은 어떻게 되나요?
Expected: policy-chat-009
Note: -

## golden-021: POLICY-chat

Q: 보낸 메시지를 수정할 수 있나요?
Expected: policy-chat-011
Note: -

## golden-022: POLICY-notification

Q: 매칭이 성사되면 알림이 오나요?
Expected: policy-notification-001
Note: 없음 — 오답 유도 가능

## golden-023: POLICY-notification

Q: 앱을 꺼놔도 휴대폰 푸시 알림이 오나요?
Expected: policy-notification-002
Note: -

## golden-024: POLICY-embedding

Q: 게시글을 몇 개 올려야 매칭 기능이 열리나요?
Expected: policy-embedding-001
Note: -

## golden-025: POLICY-embedding

Q: 게시글을 삭제하면 매칭 활성화 조건에 영향이 있나요?
Expected: policy-embedding-002
Note: 문서 자체가 "비공개" — 지어내지 않고 정직하게 답하는지 확인

## golden-026: POLICY-assistant

Q: 챗봇이 이전에 말한 내용을 기억하나요?
Expected: policy-assistant-003
Note: -

## golden-027: POLICY-assistant

Q: 챗봇 답변에 출처가 같이 나오나요?
Expected: policy-assistant-004
Note: -

## golden-028: TRAP

Q: 매칭 성공 확률은 몇 %인가요?
Expected: NONE
Note: 순수 hallucination trap

## golden-029: TRAP

Q: 채팅 메시지를 예약 전송할 수 있나요?
Expected: NONE
Note: 문서에 없는 기능

## golden-030: TRAP

Q: 오늘 서울 날씨 어때요?
Expected: NONE
Note: 서비스와 무관한 질문
