# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

bookey 백엔드 API — Spring Boot (Java 21, Maven). 서비스 API(`/api/v1/**`)와 관리자 API(`/admin/v1/**`)를 한 서버에서 제공한다. 세 저장소 중 하나: 모바일 앱은 [project-bookey-app](https://github.com/project-bookey/project-bookey-app) (Expo), 관리자 백오피스는 [project-bookey-admin](https://github.com/project-bookey/project-bookey-admin). 코드 주석의 `§F12`, `§8.2` 같은 표기는 비공개 기획서 저장소(project-bookey-docs)의 절 번호다.

코드 주석과 사용자에게 노출되는 메시지는 한국어로 쓴다.

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
```

Maven을 직접 써도 된다: `cd server && ./mvnw spring-boot:run` / `./mvnw test`.

로컬 프로필 기동 시 표본 도서 4권과 관리자 계정(`admin@bookey.local` / `bookey-local-1234`)이 시드된다. `prod`가 아니면 `POST /api/v1/auth/social`에 `provider: "DEV"`로 토큰 없이 로그인할 수 있다.

## API contract — the one rule that matters

서버가 발행하는 OpenAPI 문서(`http://localhost:8080/openapi.json`)가 두 프론트 저장소와의 유일한 계약이다. 양쪽 프론트는 이 문서에서 TypeScript 타입을 생성한다(각 저장소에서 `npm run types`).

- 응답 스키마를 바꾸면 프론트 양쪽에서 타입 재생성이 필요하다 — 계약을 깨는 변경은 세 저장소에 각각 PR이 필요하다.
- 호환되지 않는 변경은 필드를 지우기 전에 새 필드를 먼저 추가하는 식으로 단계를 나눈다.

## Architecture

`server/src/main/java/app/bookey/` 기준:

- `api/` — 서비스 API `/api/v1/**`
- `admin/` — 관리자 API `/admin/v1/**`. **필터 체인부터 분리**되어 관리자 토큰만 통과하고, 서비스 JWT는 토큰 타입 검증에서 걸린다.
- `domain/` — 엔티티·리포지토리·순수 도메인 규칙
- `batch/` — 재촉·체크포인트·알림 디스패치 스케줄러
- `common/` — 보안·에러·설정
- `support/` — 공용 유틸

환경 변수, GCP/Cloud Run 배포, 인프라 상세는 README.md 참고. 외부 도서 API 키(`KAKAO_REST_KEY` 등)가 없으면 해당 프로바이더를 건너뛰고 내부 캐시로만 검색한다(graceful degradation).
