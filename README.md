# bookey (project-bookey)

**읽기로 한 책을 진짜로 다 읽게 만들고, 진짜로 읽은 사람만 리뷰를 쓰는 독서 관리 앱**

기획서: [docs/기획서.md](docs/기획서.md)

## 핵심 가치

| 축 | 내용 |
|---|---|
| 완주 | 목표 → 진척 추적 → **지연 감지 재촉**(톤 5종, 총량 제한, 탈출구 제공) |
| 신뢰 | 실제 독서 기록이 뒷받침된 **검증 리뷰 배지** |
| 함께 | 초대 코드 하나로 참가하는 **독서 모임** — 진척 공유 + 페이지 앵커 토론 |

## 구조

```
project-bookey/
├─ server/                  Spring Boot 4.1 API (Java 21, Maven)
│   └─ src/main/java/app/bookey/
│       ├─ api/             서비스 API  (/api/v1/**)
│       ├─ admin/           관리자 API (/admin/v1/**, 별도 인증 필터)
│       ├─ domain/          엔티티 · 리포지토리 · 순수 도메인 규칙
│       └─ batch/           재촉 · 체크포인트 · 알림 디스패치 스케줄러
├─ apps/
│   ├─ mobile/              Expo(React Native) 앱 — iOS / Android
│   ├─ web/                 Next.js 공개 웹 — 독후감 SEO · 모임 초대 랜딩
│   └─ admin/               Next.js 관리자 백오피스 (별도 도메인 · 별도 배포)
├─ packages/api-types/      OpenAPI 생성 TS 타입 (앱 · 웹 · 어드민 공용)
├─ docs/                    기획서 · ADR
└─ infra/                   docker-compose 등
```

> 공개 웹과 관리자 웹은 코드·빌드·도메인·인증을 공유하지 않는다. 관리자 번들이 사용자에게 전달되는 경로를 원천 차단한다.

## 로컬 실행

```bash
# 1) DB · 캐시
docker compose -f infra/docker-compose.yml up -d

# 2) API 서버 (http://localhost:8080, 문서 /docs)
cd server && ./mvnw spring-boot:run

# 3) 앱 — 웹 미리보기 (http://localhost:8081)
cd apps/mobile && npm install && npx expo start --web

# 4) 관리자 웹 (http://localhost:3100)
cd apps/admin && npm install && npm run dev
```

### 앱을 실제 폰에서 보기

Xcode 없이도 확인할 수 있습니다.

1. App Store / Play 스토어에서 **Expo Go** 설치
2. 맥과 폰을 같은 Wi-Fi에 연결
3. `cd apps/mobile && npx expo start` 후 터미널의 QR 코드를 스캔

API 주소는 개발 서버 호스트에서 자동으로 유추합니다(맥의 LAN IP:8080).
다른 주소를 쓰려면 `EXPO_PUBLIC_API_URL` 을 지정하세요.

> 시뮬레이터로 띄우려면 Xcode(iOS) 또는 Android Studio(Android)가 필요합니다.

기동 시 로컬 프로필에서 표본 도서 4권과 관리자 계정이 시드된다.

| 항목 | 값 |
|---|---|
| 관리자 | `admin@bookey.local` / `bookey-local-1234` (`ADMIN_SEED_PASSWORD` 로 변경) |
| API 문서 | http://localhost:8080/docs |
| Postgres | `localhost:55432` (bookey / bookey_local) |
| Redis | `localhost:56379` |

### 개발용 로그인

`prod` 프로필이 아니면 `DEV` provider 로 토큰 없이 로그인할 수 있다.

```bash
curl -X POST http://localhost:8080/api/v1/auth/social \
  -H 'Content-Type: application/json' \
  -d '{"provider":"DEV","token":"tester1","nickname":"테스터"}'
```

## 환경 변수

| 키 | 설명 |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL 접속 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 접속 |
| `JWT_SECRET` | 32바이트 이상. **운영에서는 반드시 교체** |
| `KAKAO_REST_KEY` | 카카오 책 검색 (1차 검색) |
| `ALADIN_TTB_KEY` | 알라딘 OpenAPI (페이지 수 보강) |
| `GOOGLE_BOOKS_KEY` | Google Books (해외서 폴백, 선택) |

키가 없으면 해당 프로바이더를 건너뛰고 내부 캐시로만 검색한다 (graceful degradation).

## 테스트

```bash
cd server && ./mvnw test
```
