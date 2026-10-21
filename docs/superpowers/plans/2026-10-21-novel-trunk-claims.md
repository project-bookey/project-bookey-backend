# 릴레이노벨 첫 작가의 줄기 · 열매 = 이어 쓸 수 있는 회차 · 가지 2~5 — 구현 기록

2026-10-21. 2026-10-19 기록(`2026-10-19-novel-branch-bin-sunlight.md`)의 배달 규칙을 바꾼다. 마이그레이션 V67.

## 사용자 결정

- **가지 수**: 회차 하나에 붙는 가지는 2~5개(기본 3, `novels.branch_limit`). 예전 작품(10~20)은 5로 맞춘다.
- **무작위 배달 없앰**: 회차를 사람에게 보내지 않는다. 홈 열매 하나는 지금 이어 쓸 수 있는 회차 하나다.
  - 고르는 순서: 최근에 쓴 회차(하루 안 → 일주일 안 → 그 전) → 작품 장르가 내 관심 장르와 맞는 회차, 같은 묶음 안에서는 무작위.
  - 새로고침하면 일주일 안에 보여 준 적 없는 회차로 바꾼다(`novel_home_seen`). 처음 열거나 놓인 열매가 모두 더 이어 쓸 수 없게 되면 횟수를 쓰지 않고 새 묶음을 놓는다(그때는 보여 준 적 있는 회차도 놓을 수 있다).
  - 빼는 회차: 내 작품, 내가 쓴 회차, 내가 이미 이어 쓴 회차, 탈퇴한 사람의 것, 이미 자리를 맡은 작품의 회차, 가지 자리가 없거나 목표 회차에 닿은 회차.
- **자리 맡기(CLAIM)**: 열매를 골라 쓰기 시작하면(`POST /novels/{id}/chapters/{chapterId}/claim`) 작품의 제한 시간 동안 그 회차의 가지 자리를 하나 맡는다. 쓰지 않고 시간이 지나거나 내려놓으면 자리가 다시 빈다(다른 사람에게 보내지 않는다). 한 작품에서 자리는 하나만 맡는다. 내 작품·내가 쓴 회차에는 자리를 맡지 않는다(`NOVEL_CLAIM_NOT_ALLOWED`) — 내 회차는 햇살로 잇는다(SELF). 한 사람은 한 회차에 가지 하나만 뻗는다(이미 뻗었으면 `NOVEL_ALREADY_BRANCHED`) — 홈 열매에서 빼는 조건과 같다.
- **첫 작가의 줄기**: 만든 사람은 마감 없이 무료로 자기 줄기 끝 회차에서만 위로 이어 쓴다(`/novels/{id}/draft`·`/chapters`, `turnNumber`). 줄기 회차(`novel_chapters.trunk`)는 가지 자리에 들지 않고 수거되지 않는다. 앱은 줄기를 나무의 큰 줄기로 그린다.

## 바뀐 곳

- `NovelDeliveryService`: 무작위로 사람 고르기(`pickRecipients`)·`deliverNewChapter`·`resend` 를 없애고 `claim`·`canClaim` 을 둔다. 가지 자리는 줄기를 뺀 자식(`countBranchChildren`) + 맡은 자리로 센다.
- `NovelService`: OPEN 작품의 1회차와 그 뒤 첫 작가의 회차는 `addTrunkChapter` 로 줄기 끝에 잇는다. `canWrite` 는 CIRCLE 이면 차례, OPEN 이면 `Novel.canGrowTrunk`.
- `NovelHomeFeedService`: 작품 대신 회차를 고른다(`NovelChapterRepository.pickHomeFruits`·`continuableAmong`·`markSeen`). 묶음은 `novel_home_feeds.chapter_ids` 에 둔다.
- `NovelBranchBinService`: 지운 가지의 부모에 다시 배달하지 않는다 — 자리가 비면 그 회차가 다시 열매 후보가 된다. 줄기는 수거하지 않는다.
- 응답: `NovelHomeFeedView.fruits`(`NovelHomeFruitView`) 추가, `novels` 는 예전 앱 호환용. `NovelChapterView` 에 `trunk`·`canClaim`·`slotsLeft`, `NovelChapterNode` 에 `trunk`, `NovelDeliveryView.source` 에 `CLAIM`.

## 남은 일

- 운영 배포 전에 로컬 DB에서 V67 적용·기동과 `pickHomeFruits` 실행 확인(이 작업 때는 Docker가 응답하지 않아 단위 테스트까지만 확인).
