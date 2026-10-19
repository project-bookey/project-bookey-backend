# 릴레이노벨 가지 상한 · 나뭇가지 수거함 · 햇살 — 구현 기록

2026-10-19. 릴레이노벨 앱(`relay-novel`)의 열매로 퍼뜨리기(OPEN) 작품 개편. 마이그레이션 V65.

## 사용자 결정

- **배달 인원**: 고르지 않는다. 새 회차는 늘 10명에게 먼저 간다(`Novel.DELIVERY_COUNT`).
- **가지 상한**: 작품을 만들 때 한 회차에 붙을 수 있는 가지 수를 10~20(기본 10)으로 정한다(`novels.branch_limit`).
  나무에 있는 가지(ACTIVE)·수거함의 가지(PRUNED)·들고 있는 열매(DELIVERED)가 자리를 차지한다. 자리가 다 차면 배달·재배달하지 않는다.
- **나뭇가지 수거함**: 판정일(쓴 날, 살리면 살린 날)부터 한 달이 지나도 조회가 50회를 못 넘긴 **끝 가지**를 나무에서 숨기고 글쓴이의 수거함에 둔다.
  - 조회는 한 사람당 한 번, 글쓴이 본인 제외(`novel_chapter_views`). `GET /novels/{id}/chapters/nodes/{chapterId}`를 열 때 센다. 누적으로 센다.
  - 일주일 안에 햇살 3개로 **살리거나**(한 달 뒤 다시 판정) **버린다**. 고르지 않으면 지운다(REMOVED). 지워지면 부모 회차에 자리가 생겨 한 사람에게 다시 배달한다.
  - 끝 가지가 지워져 새로 끝이 된 중간 가지도 같은 기준으로 판정한다(다음 배치에서 걸린다).
  - 뿌리 회차·완결 작품·모여 만들기(CIRCLE)는 수거하지 않는다. 수거하면 그 회차를 들고 있던 열매는 거둔다.
- **내 글 잇기**: 내가 쓴 회차에 햇살 5개를 내면 그 회차를 나에게 배달한다(`source = SELF`, 마감 7일). 받은 열매처럼 초안·발행한다.
  그만두거나 마감이 지나면 낸 햇살을 돌려주고 다른 사람에게 보내지 않는다. 이 가지도 상한에 든다. 작품당 열매 하나 들기 규칙은 그대로다.
- **햇살**: 북키 책갈피와 따로 둔 소설 전용 재화. 인앱 소모형 상품 `relaynovel.sunlight.10/30/100`(1,100·3,300·9,900원)으로 채운다.

## 스키마 (V65__novel_branch_bin_sunlight.sql)

- `novels.branch_limit` INTEGER 10~20, 기존 OPEN 작품의 `delivery_count`는 10으로 맞춘다.
- `novel_chapters`: `state`(ACTIVE·PRUNED·REMOVED), `view_count`, `judged_from`(기존은 `created_at`), `pruned_at`, `purge_at`.
- `novel_chapter_views(chapter_id, user_id)` PK — 한 사람 한 번.
- `novel_deliveries`: `source`(RANDOM·SELF), `sunlight_paid`. 같은 회차를 한 사람에게 두 번 보내지 않는 유니크는 무작위 배달에만 건다(`uq_novel_deliveries_random_once`) — 내 글 잇기는 자리가 남는 한 여러 번 할 수 있다.
- 햇살: `novel_sunlight_wallets`(잔액 ≥ 0), `novel_sunlight_transactions`(원장, 늘면 양수·쓰면 음수), `novel_sunlight_purchases`(주문, `order_id`·`transaction_id` 유니크).

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/novels` | `branchLimit`(10~20) 추가. `deliveryCount`는 예전 앱 호환용으로 받기만 한다 |
| GET | `/novels/{id}/chapters/nodes/{chapterId}` | `viewCount`·`canSelfContinue`·`selfContinueCost` 추가. 수거함의 가지는 404 |
| POST | `/novels/{id}/chapters/{chapterId}/self-delivery` | 내 글 잇기. `NovelDeliveryView`(+`source`) |
| GET | `/novels/me/branch-bin` | 수거함 목록 + `restoreCost` |
| POST | `/novels/me/branch-bin/{chapterId}/restore` | 살리기(햇살 모자라면 402 `NOVEL_SUNLIGHT_INSUFFICIENT`) |
| POST | `/novels/me/branch-bin/{chapterId}/discard` | 버리기 |
| GET | `/novels/me/sunlight` | 잔액·쓰는 값·충전 상품 |
| POST | `/novels/sunlight-purchases/checkout`·`/verify` | 햇살 충전 주문·스토어 확인 |

`NovelSummary`에는 `branchLimit`을 더하고 `deliveryCount`(늘 10)는 남겨 둔다 — 응답 필드는 지우기 전에 새 필드를 먼저 더한다.
새 오류 코드: `NOVEL_SUNLIGHT_INSUFFICIENT`(402), `NOVEL_BRANCH_FULL`·`NOVEL_BRANCH_HOLDING`·`NOVEL_BIN_GONE`(409).

## 구현 메모

- 배달 자리 계산은 `NovelDeliveryService.freeSlots`. 배달·재배달은 작품을 잠근 트랜잭션 안에서만 한다.
- 배치(`NovelMaintenanceJob`, 10분마다): `pruneStaleBranches`(→ `NovelBranchBinService.pruneIfStale`), `purgeBranchBin`(→ `purgeIfDue`). 후보는 100개씩, id마다 작품을 먼저 잠그고 회차를 새로 읽어 다시 확인한다.
- 햇살 결제 확인은 새로고침 결제와 같은 흐름이고, 스토어 자격 증명(북키 키 + 릴레이노벨 앱 id)은 `NovelStoreCredentials`로 함께 쓴다. 앱 id는 `bookey.novel.sunlight.apple-bundle-id`·`google-package-name`(기본 `app.relaynovel.mobile`).
- 값(30일·50회·7일)은 `NovelChapter`의 상수, 가격(3·5)과 상품은 `bookey.novel.sunlight` 설정.

## 남은 일

- 스토어(App Store·Play)에 `relaynovel.sunlight.10/30/100` 소모형 상품 등록.
- 운영 배포 전에 로컬 DB에서 V65 적용·기동 확인(이 작업 때는 Docker가 꺼져 있어 단위 테스트까지만 확인).
