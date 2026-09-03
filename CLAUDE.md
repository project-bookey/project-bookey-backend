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

로컬 프로필 기동 시 `LocalDataSeeder`가 표본 도서 4권과 관리자 계정(`admin@bookey.local` / `bookey-local-1234`)을 시드한다. 개발용 사용자 로그인은 이메일 가입·로그인으로 한다 — `POST /api/v1/auth/signup` `{"email","password","nickname"}` 으로 가입하면 응답에 토큰이 바로 담기고, 이후 `POST /api/v1/auth/login` `{"email","password"}` 으로 로그인한다. 스모크 스크립트는 `tester1@dev.local` / `password1234` 처럼 로그인 실패 시 가입으로 폴백하는 관례를 쓴다. 예전의 `provider: "DEV"` 소셜 로그인은 제거됐다(`AuthProvider` 는 `APPLE`·`GOOGLE`·`KAKAO` 만, `AuthServiceTest` 가 고정).

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

### DB 마이그레이션

`ddl-auto: validate` — 엔티티를 바꾸면 반드시 `server/src/main/resources/db/migration/V<n>__*.sql` 마이그레이션을 추가해야 하고, 스키마와 엔티티가 정확히 일치해야 기동된다.

### 도서 검색 파이프라인 (`BookSearchService`)

내부 캐시(books 테이블) → 카카오 검색 → 국내 0건이면 Google Books 폴백 → isbn13 기준 upsert → 페이지 수 없는 책은 알라딘으로 **비동기** 보강. 외부 API 키(`KAKAO_REST_KEY` 등)가 없으면 해당 프로바이더를 건너뛰고 내부 캐시로만 검색한다(graceful degradation). API 키는 서버에만 둔다.

### 도메인 규칙은 순수 클래스로

진척 계산(`ProgressCalculator`), 지연 레벨(`LagLevel`), 배너 활성 판정(`Banner.isActiveAt`) 같은 규칙은 Spring에 의존하지 않는 순수 로직으로 두고 단위 테스트한다. 배치 잡은 이 규칙을 재사용해 재촉 알림 후보를 만들며, 알림 총량 제한(`bookey.notification.*-cap`)이 걸려 있다.

### 설정

도메인 상수(JWT TTL, 재촉 쿨다운, 알림 캡 등)는 `application.yml`의 `bookey:` 트리 → `BookeyProperties`로 바인딩된다. 하드코딩하지 말 것. 환경 변수, GCP/Cloud Run 배포, 인프라 상세는 README.md 참고.

## Spring Boot 4 주의점

- Jackson 3: import가 `tools.jackson.databind.ObjectMapper` (`com.fasterxml` 아님).
- Flyway 자동설정은 `spring-boot-flyway` 모듈이 별도로 필요하다 (`flyway-core`만으로는 실행 안 됨).

## 테스트 관례

**Spring 컨텍스트 없는 순수 단위 테스트만 존재한다** (JUnit5 + AssertJ). `@SpringBootTest`/MockMvc 선례가 없으니 만들지 않는다 — 컨트롤러 로직은 서비스·순수 로직으로 밀어내 단위 테스트하고, 라우팅은 서버 기동 스모크로 확인한다. `@DisplayName`은 한국어.
