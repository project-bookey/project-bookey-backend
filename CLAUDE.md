# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

bookey 백엔드 API — Spring Boot 4.1 (Java 21, Maven 단일 모듈 `server/`). 서비스 API(`/api/v1/**`)와 관리자 API(`/admin/v1/**`)를 한 서버에서 제공한다. 세 저장소 중 하나: 모바일 앱은 [project-bookey-app](https://github.com/project-bookey/project-bookey-app) (Expo), 관리자 백오피스는 [project-bookey-admin](https://github.com/project-bookey/project-bookey-admin). 코드 주석의 `§F12`, `§8.2` 같은 표기는 비공개 기획서 저장소(project-bookey-docs)의 절 번호다.

코드 주석과 사용자에게 노출되는 메시지는 한국어로 쓴다. `@Operation(summary)`, `@DisplayName`, 에러 메시지도 한국어. 구현 계획 문서는 `docs/superpowers/plans/`에 둔다.

## Git 규칙

- 커밋 메시지·PR 본문에 AI 흔적을 절대 남기지 않는다. `Co-Authored-By: Claude ...` 트레일러, "Generated with Claude Code" 문구 등 어떤 형태의 어트리뷰션도 넣지 말 것. 커밋은 순수하게 변경 내용만 기술한다.
- 커밋은 의미 있는 변경끼리 확실히 묶는다 — 너무 잘게 쪼개지 않는다. 메시지는 사람이 읽고 바로 이해할 수 있게 쓰고, 첫 줄에서 해당 작업이 신규인지 수정인지 알 수 있게 한다 (예: `신규: ...` / `수정: ...`).
- 작업은 main에서 직접 하지 않고 작업별 브랜치를 만들어 진행한다. 작업이 완료되고 검증(테스트 등)에 이상이 없으면 main에 머지한 뒤 해당 브랜치를 삭제한다.
- 브랜치 삭제는 사용자(bottleOne) 또는 Claude가 만든 브랜치에만 한다. 다른 사람이 만든 브랜치는 절대 삭제하지 않는다.

## Commands

```bash
npm run infra:up     # Postgres(localhost:55432) + Redis(localhost:56379) via docker compose
npm run dev          # API 서버 기동 — http://localhost:8080 (문서 /docs, OpenAPI /openapi.json)
npm test             # cd server && ./mvnw test — the only check
npm run infra:reset  # 볼륨까지 지우고 인프라 재기동

cd server && ./mvnw test -Dtest=BannerServiceTest            # 단일 클래스
cd server && ./mvnw test -Dtest=BannerServiceTest#메서드명    # 단일 메서드
```

로컬 프로필 기동 시 `LocalDataSeeder`가 표본 도서 4권과 관리자 계정(`admin@bookey.local` / `bookey-local-1234`)을 시드한다. 개발용 사용자 로그인은 이메일 가입·로그인으로 한다 — 가입 인증은 `bookey.auth.signup-verification` 설정을 따른다(기본 IDENTITY=휴대폰 본인인증). 로컬은 포트원 키가 없으므로 개발 스텁이 켜져 있어 `POST /api/v1/auth/signup` `{"email","password","nickname","identityVerificationId":"dev-아무값"}` 으로 가입한다(같은 dev id = 같은 CI → 중복 가입 차단 시험 가능). EMAIL_CODE 모드로 돌리면 ① `POST /api/v1/auth/email/code` 로 코드 발급(`devCode` 동봉) ② signup 에 `code` 전달. 코드는 3분(`email-code.ttl`) 동안 쓸 수 있고 앱이 발급 응답의 `expiresInSec` 으로 남은 시간을 센다. 다시 받기는 기다림 없이 바로 되지만(사용자 결정, 2026-10-05), 같은 이메일·용도로 1시간에 5번(`hourly-limit`, Redis `RateLimiter`, 2026-10-05 사용자 결정으로 10 → 5)을 넘기면 `RATE_LIMITED` — 메일 폭탄·코드 바꿔 가며 대입을 막는 장치라 없애지 않는다. 발급 응답의 `resendsLeft` 는 그 1시간 안에 더 받을 수 있는 수(Redis 장애로 세지 못하면 비운다), `sendLimit` 은 상한으로, 앱이 '다시 받기' 버튼에 받은 수/상한(예: 3/5)으로 보여 준다. 가입 폼의 사전 확인(`/email/code/verify`)을 통과한 코드는 약관을 읽는 사이 만료되지 않게 `verified-ttl`(30분)까지 늘어난다. 가입 화면 구성은 `GET /api/v1/auth/signup-config` 로 조회한다. 이후 `POST /api/v1/auth/login` `{"email","password"}` 으로 로그인한다. 스모크 스크립트는 `tester1@dev.local` / `password1234` 처럼 로그인 실패 시 코드 발급→가입으로 폴백하는 관례를 쓴다. 소셜 로그인(`/auth/social`)은 연동된 계정이면 로그인하고, **처음 보는 소셜 계정은 바로 가입시킨다**(`newUser=true`, 비밀번호 없는 소셜 전용 계정). provider 이메일이 기존 계정과 같으면 자동 병합하지 않고 `EMAIL_ALREADY_EXISTS` — 이메일로 가입한 사람은 로그인한 뒤 `POST /auth/social/link` 로 연동하고 `DELETE /auth/social/link/{provider}` 로 해제한다. 비밀번호 없는 계정의 마지막 연동은 해제할 수 없다(`LAST_LOGIN_METHOD`). 연동 상태는 `MeResponse.linkedProviders`·`hasPassword` 로 내려간다(`AuthServiceTest` 가 고정). `SOCIAL_SIGNUP_DISABLED` 는 예전 '연동 계정만' 시절의 코드로 지금은 던지지 않는다. 비밀번호 찾기는 ① `POST /api/v1/auth/password/code` `{"email"}` 로 재설정 코드 발급(가입된 이메일만 — 없으면 `EMAIL_NOT_REGISTERED`, 로컬은 `devCode` 동봉) ② `POST /api/v1/auth/password/reset` `{"email","code","newPassword"}` — 성공하면 기존 리프레시 토큰을 모두 폐기하고 바로 로그인 토큰을 준다. 소셜 전용 계정도 이 경로로 비밀번호가 생긴다. 가입 코드와 재설정 코드는 `email_verifications.purpose`(`SIGNUP`·`PASSWORD_RESET`)로 나뉘어 서로 대신 쓸 수 없다. 예전의 `provider: "DEV"` 소셜 로그인은 제거됐다(`AuthProvider` 는 `APPLE`·`GOOGLE`·`KAKAO` 만).

## API contract — the one rule that matters

서버가 발행하는 OpenAPI 문서(`http://localhost:8080/openapi.json`)가 두 프론트 저장소와의 유일한 계약이다. 양쪽 프론트는 이 문서에서 TypeScript 타입을 생성한다(각 저장소에서 `npm run types`).

- 응답 스키마를 바꾸면 프론트 양쪽에서 타입 재생성이 필요하다 — 계약을 깨는 변경은 세 저장소에 각각 PR이 필요하다.
- 호환되지 않는 변경은 필드를 지우기 전에 새 필드를 먼저 추가하는 식으로 단계를 나눈다.
- 응답 DTO record 이름이 곧 스키마 이름이다. 확정된 이름(`BannerView` 등)은 바꾸지 않는다.
- `OpenApiRequiredFieldsConfig`가 record의 원시 타입·컬렉션·`@NotNull` 컴포넌트를 required로 내보낸다 — DTO는 자바 record로 작성해야 이 로직을 탄다.

## Architecture

`server/src/main/java/app/bookey/` 기준 피처 레이어링:

```
api/<feature>/      컨트롤러 + 서비스 + dto/XxxDtos.java (record 홀더 하나)
domain/<feature>/   엔티티 + 리포지토리 + 순수 도메인 규칙 (예: ProgressCalculator, LagLevel)
admin/              관리자 API (/admin/v1/**) — 모더레이션·제재·감사로그
batch/              @Scheduled 잡 (지연 감지, 완독 임박, 모임 체크포인트, 알림 디스패치, 세션 정리)
common/             보안 · 에러 · 설정 · 공용 유틸
```

새 피처는 이 구조를 그대로 따른다: `domain/<feature>`에 엔티티·리포지토리, `api/<feature>`에 컨트롤러·서비스·`XxxDtos`.

### 보안: 필터 체인 2개가 완전히 분리

`SecurityConfig`에 체인이 둘이다. `/admin/v1/**`은 `ADMIN_ACCESS` 토큰만, 나머지는 `USER_ACCESS` 토큰만 통과한다 — 서비스 JWT로는 관리자 API에 접근 불가. CORS도 체인별로 별도(어드민은 `localhost:3100` / `admin.bookey.app`만).

- 컨트롤러에서 `@AuthenticationPrincipal AuthUser` / `AuthAdmin`으로 주입받는다.
- 관리자 권한은 애너테이션이 아니라 **컨트롤러에서 수동 체크**가 관례: `admin.role().canManageOps()` 실패 시 `ApiException.of(ErrorCode.ADMIN_FORBIDDEN)`.
- 관리자 로그인은 이메일+비밀번호 후 TOTP 2단계.

### 에러 처리

`ErrorCode` enum(한국어 메시지) → `ApiException.of(...)` → `GlobalExceptionHandler`. 클라이언트는 `code` 문자열로 분기하므로 새 에러는 enum에 추가한다.

### UX 라이팅 (2026-10-04)

앱은 서버 문구(`message`, 입력 검증 `errors[].reason`, 알림 제목·본문, 인증 메일)를 그대로 보여 준다. 사용자에게 보이는 문구는 앱과 같은 원칙으로 쓴다.

- 해요체 하나로 쓴다 — '~습니다'는 쓰지 않는다(관리자 전용 코드·로그·Swagger 설명은 예외). 실패는 "~하지 못했어요" 뒤에 할 일을 붙인다.
- 앱과 같은 용어: 클럽(club)과 모임(meeting)을 구분한다 — 클럽을 '모임', 모임을 '약속'이라 부르지 않는다. 클럽에 남기는 사진·한 줄은 '메모', 완독·하차 소감은 '한 줄평', 그 밖에 진도·멤버·같이 읽기. 세션·토큰·검증 같은 개발 용어는 쓰지 않는다.
- 닉네임·책 제목 같은 변수 바로 뒤에 바뀌는 조사(을/를, 이/가)를 붙이지 않는다 — 문장을 바꿔 피한다(예: `"제목"에 좋아요를 눌렀어요`). 닉네임 뒤 '님'은 붙여 쓴다.
- 입력 검증 기본 문구는 `ValidationMessages.properties`가 덮는다. 최소 길이가 있는 `@Size`나 목록에 거는 `@Size`는 기본 문구('{max}자까지')가 맞지 않으니 그 자리에서 `message`를 준다.

### DB 마이그레이션

`ddl-auto: validate` — 엔티티를 바꾸면 반드시 `server/src/main/resources/db/migration/V<n>__*.sql` 마이그레이션을 추가해야 하고, 스키마와 엔티티가 정확히 일치해야 기동된다.

- **V36 은 SQL 이 아니라 자바 마이그레이션이다** — `server/src/main/java/app/bookey/common/migration/V36__Remove_book_quotes.java`(밑줄 기능 삭제: 엮인 밑줄을 독후감 본문으로 옮기고 밑줄 테이블을 지운다). `db/migration` 폴더에는 V35 다음이 안 보이지만 **새 SQL 마이그레이션은 V37 부터** 매긴다 — V36 을 또 만들면 버전이 겹쳐 서버가 뜨지 않는다.
- 이 클래스와 그 규칙(`domain/post/LegacyQuoteInliner`)은 쓰이지 않는 코드처럼 보여도 **지우면 안 된다** — 이미 적용된 V36 을 Flyway 가 찾지 못하면 기동이 실패한다.
- **V52 도 자바 마이그레이션이다** — `common/migration/V52__Release_flagged_reviews.java`(어뷰징 감지 제거 뒤 '의심(FLAGGED)' 리뷰를 감지 없는 규칙으로 다시 매기고 예전 스냅숏은 `previous` 에 남긴다). `db/migration` 에 V51 다음이 안 보여도 **새 SQL 마이그레이션은 V53 부터** 매기고, 이 클래스도 지우지 않는다.
- **V56(`postcards.opened_at`)은 쓰지 않는 칼럼이다** — 엽서 열람(받은 사람이 연 시각) 기능을 운영에 배포했다가 같은 날 사용자 요청으로 코드만 되돌렸다(2026-10-05). 칼럼과 V56 파일은 남겨 둔다 — 운영 DB 에 이미 적용돼 파일을 지우면 기동이 실패한다. 엔티티에 매핑하지 않은 칼럼이라 `validate` 와도 부딪히지 않는다. 열람을 다시 넣을 때 이 칼럼을 쓰면 된다(기존 엽서는 받은 때 연 것으로 채워져 있다).

### 도서 검색 파이프라인 (`BookSearchService`)

내부 캐시(books 테이블) → 카카오 검색 → 국내 0건이면 Google Books 폴백 → isbn13 기준 upsert → 페이지 수 없는 책은 알라딘으로 **비동기** 보강. 외부 API 키(`KAKAO_REST_KEY` 등)가 없으면 해당 프로바이더를 건너뛰고 내부 캐시로만 검색한다(graceful degradation). API 키는 서버에만 둔다.

### 모임(클럽) 모델 (2026-10-03, V40)

모임은 기간이 없고(`clubs.ends_at` 은 예전 모임만, 호스트가 끝낼 때 끝난다) 책 한 권에 묶이지 않는다. 만남은 멤버 누구나 열고(고치기·취소는 연 사람과 호스트만, 2026-10-05), 연 사람은 늘 참여자다(만들 때 넣고 참여 취소는 거절, 예전 만남은 V54 가 채움). 같이 읽기 시작과 모임 노트 쓰기(연산·사진·실시간 연결)는 그 만남의 참여자만 — 노트는 비참여자에게 `readOnly`·`attending=false` 로 보이고 쓰기는 `MEETING_NOTE_READ_ONLY` 로 거절한다(예전 앱도 잠기게). 만남(`club_meetings.book_id`, 선택)마다 책을 고르고, 다가오는 만남의 책이 `clubs.current_club_book_id`(지금 읽는 책)가 된다 — 판정은 순수 규칙 `ClubCurrentBook`, 반영은 `ClubService.syncCurrentBook`(만남 생성·수정·취소 때 + 매일 00:05 배치). 지금 책이 바뀌면 멤버마다 그 책의 읽기 기록을 `ClubMember.readingRecordId` 로 다시 잇는다(없으면 서재에 WANT_TO_READ 로 추가) — 진척·지금 읽는 중·스포일러 가림이 이 기록을 본다. 아직 책이 없는 모임의 멤버는 `readingRecordId` 가 null 이니 조회 시 null 키를 다룰 것. 조각·글은 쓸 때의 `club_book_id` 에 붙고, 지난 책의 글은 그 책의 뷰어 진도로 가린다(`ClubPostService.viewerStates`). 체크포인트·결산·기간 종료 배치는 걷어냈고(테이블은 남김), `daysLeft`·`checkpoints`·`nextCheckpoint` 는 예전 앱 호환용으로만 응답에 남아 있다.

**나가기·내보내기 (2026-10-05, 사용자 결정)**: 멤버 행은 지우지 않고 `LEFT`/`KICKED` 로 두지만, `ClubService.forgetMemberTraces` 가 그 사람의 채팅 이용권(`club_chat_unlocks` — 다시 참가하면 책갈피를 다시 낸다)·채팅 읽음 위치·아직 시작하지 않은 만남 참여를 지운다. 다시 참가하면 같은 행이 `rejoin()` 으로 살아나며 `joinedAt` 이 새로 찍힌다. 채팅(`ClubCommunityService.chatMessages`)은 보낸 사람이 지금 ACTIVE 이고 그 `joinedAt` 뒤에 보낸 메시지만 그 사람 것으로 내리고, 나머지는 `senderId` 없이 '나간 멤버'·`mine=false` 로 내린다 — 다시 참가한 사람의 예전 메시지도 내 것이 아니다. 메모(조각)는 그대로 남는다.

### 탈퇴한 사람의 기록은 보이지 않는다 (2026-10-05, 사용자 요청)

탈퇴를 요청하면 계정 행은 익명화된 채(`TERMINATED`) 30일 남았다가 `AccountDeletionJob` 이 지운다. 그 30일 동안에도 그 사람의 기록은 다른 사람에게 보이지 않는다 — 데이터는 지우지 않고 조회에서 뺀다. 판정은 계정 상태 `TERMINATED` 하나라 운영팀이 계정을 종료한 사람도 같다.

- 목록 쿼리는 `x.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')`(네이티브는 `users`)를 건다: 독후감(피드·HOT·책별·클럽별), 독후감·리뷰 댓글과 그 수(탈퇴한 사람의 댓글에 달린 답글도 빠진다), 리뷰 목록·평점, 한 줄평, 광장 완독, 좋아요 수·좋아요 누른 사람, 책 좋아요 수, 팔로워·팔로잉 목록과 수, 받은·보낸 엽서, 1:1 채팅 목록(상대가 탈퇴한 방), 클럽 채팅 메시지와 안 읽은 수, 클럽 글·메모·댓글.
- 한 건 조회는 `UserRepository.isTerminated` 로 없는 것처럼 답한다: 독후감(`PostService.readable`), 공개 블로그, 리뷰 상세와 댓글, 클럽 글, 프로필·남의 독후감·서재·통계(`ProfileService.profile`·`requireUser` → 404), 1:1 채팅방, 엽서 답장, 팔로우·엽서·채팅 시작.
- 모임 참여자·모임 노트 참여자 목록에서도 뺀다. 모임 노트 내용은 함께 만든 것이라 남긴다.
- 탈퇴하는 순간(`AccountEraser`) 참가한 클럽을 모두 나가고(`ClubService.leaveAllOnWithdrawal` — 호스트면 탈퇴하지 않은 활성 멤버 중 운영진 → 먼저 들어온 사람에게 넘기고, 남은 사람이 없으면 클럽을 끝낸다), 그 사람이 한 일로 남에게 간 알림(본문에 닉네임이 굳어 있는 좋아요·댓글·팔로우·엽서·채팅·찌르기 알림, payload 의 `fromUserId`·`commenterId`·팔로우의 `userId`)을 지운다. 이 처리 전에 탈퇴한 사람과 실패한 경우는 매일 04:00·04:05 배치가 다시 한다.
- 새 조회를 만들 때 남의 기록을 내려준다면 같은 조건을 건다. 테스트의 `mock(UserRepository.class)` 는 `isTerminated` 가 false 다.

### 차단 (2026-10-05, 사용자 결정, V55)

한 방향(`user_blocks`: blocker → blocked), 상대에게 알리지 않는다. 막는 범위는 **엽서·채팅만** — 광장의 독후감·댓글·팔로우는 그대로다. `BlockService` 가 규칙을 모은다.

- 보내기 전 `requireReachable(보내는 사람, 받는 사람)`: 엽서 보내기·답장, 채팅 열기·메시지. 내가 막았으면 `USER_BLOCKED`(설정의 '차단한 사람'에서 풀라는 안내), 상대가 날 막았으면 `USER_UNREACHABLE`(막았다는 말은 하지 않는다).
- 막은 사람에게서만 숨긴다: 받은·보낸 엽서 목록과 채팅 목록 쿼리가 `NOT EXISTS (SELECT b FROM UserBlock b WHERE b.blockerId = :me AND ...)` 를 건다. 막은 사람이 그 방을 열면 `CHAT_NOT_FOUND`. 막힌 사람에게는 방이 그대로 보이고 읽을 수 있다(보내기만 막힌다). `canChat` 은 어느 쪽이든 막혔으면 false.
- 데이터는 지우지 않는다 — 풀면(`DELETE /blocks/{userId}`) 엽서·채팅방이 다시 보인다. 차단·해제는 모두 멱등이고, 나 자신은 막을 수 없다(`INVALID_REQUEST`), 탈퇴한 사람은 `NOT_FOUND`. 목록(`GET /blocks`)에서 탈퇴한 사람은 뺀다.
- 막는 범위를 넓히려면(광장 숨김 등) 이 절을 먼저 고치고 같은 `NOT EXISTS` 조건을 그 목록 쿼리에 건다.

### 어뷰징 감지 없음 (2026-10-05, 사용자 결정)

한 번에 많이 읽거나 빨리 완독해도 의심하지 않는다. 세션의 비정상 속도(분당 5쪽 초과)·타이머 방치·4시간 초과 플래그와 리뷰의 순간 완독·하루 대량 완독(`FLAGGED`) 판정을 걷어냈다 — 관리자에게 완독을 증명하게 만들던 장치다. 4시간 초과 세션을 4시간으로 잘라 닫는 것은 그대로다. 예전에 '의심'으로 묶인 리뷰는 V52 가 풀었다. `reading_sessions.abuse_flags`·`counted_for_verification` 컬럼은 예전 값과 함께 남겨 두되 읽지 않고, 응답의 `SessionView.abuseFlags`(빈 목록)·`countedForVerification`(true)·`VerificationPreview.flags`(빈 목록)는 예전 앱 호환용이다. 감지를 다시 넣지 않는다.

### 도메인 규칙은 순수 클래스로

진척 계산(`ProgressCalculator`), 지연 레벨(`LagLevel`), 배너 활성 판정(`Banner.isActiveAt`) 같은 규칙은 Spring에 의존하지 않는 순수 로직으로 두고 단위 테스트한다. 배치 잡은 이 규칙을 재사용해 재촉 알림 후보를 만들며, 알림 총량 제한(`bookey.notification.*-cap`)이 걸려 있다.

### 설정

도메인 상수(JWT TTL, 재촉 쿨다운, 알림 캡 등)는 `application.yml`의 `bookey:` 트리 → `BookeyProperties`로 바인딩된다. 하드코딩하지 말 것. 환경 변수, GCP/Cloud Run 배포, 인프라 상세는 README.md 참고.

## Spring Boot 4 주의점

- Jackson 3: import가 `tools.jackson.databind.ObjectMapper` (`com.fasterxml` 아님).
- Flyway 자동설정은 `spring-boot-flyway` 모듈이 별도로 필요하다 (`flyway-core`만으로는 실행 안 됨).

## 테스트 관례

**Spring 컨텍스트 없는 순수 단위 테스트만 존재한다** (JUnit5 + AssertJ). `@SpringBootTest`/MockMvc 선례가 없으니 만들지 않는다 — 컨트롤러 로직은 서비스·순수 로직으로 밀어내 단위 테스트하고, 라우팅은 서버 기동 스모크로 확인한다. `@DisplayName`은 한국어.
