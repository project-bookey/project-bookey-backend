# project-bookey-backend

**읽기로 한 책을 진짜로 다 읽게 만들고, 진짜로 읽은 사람만 리뷰를 쓰는 독서 관리 앱**

bookey 백엔드 API 저장소입니다. 프론트는 각각 별도 저장소에 있습니다.

| 저장소 | 내용 |
|---|---|
| **project-bookey-backend** (여기) | 백엔드 API · 인프라 |
| **[project-bookey-app](https://github.com/project-bookey/project-bookey-app)** | 모바일 앱 (Expo) |
| **[project-bookey-admin](https://github.com/project-bookey/project-bookey-admin)** | 관리자 백오피스 (Next.js) |

기획서는 비공개 저장소에 있습니다: **[project-bookey-docs](https://github.com/Jay-0315/project-bookey-docs)**
코드 주석의 `§F12`, `§8.2` 같은 표기는 그 기획서의 절 번호를 가리킵니다.

## 핵심 가치

| 축 | 내용 |
|---|---|
| 완주 | 목표 → 진척 추적 → **지연 감지 재촉**(톤 5종, 총량 제한, 탈출구 제공) |
| 신뢰 | 실제 독서 기록이 뒷받침된 **검증 리뷰 배지** |
| 함께 | 초대 코드 하나로 참가하는 **독서 모임** — 진척 공유 + 페이지 앵커 토론 |

## 구성

```
project-bookey-backend/
├─ server/                  Spring Boot 4.1 API (Java 21, Maven)
│   └─ src/main/java/app/bookey/
│       ├─ api/             서비스 API  /api/v1/**
│       ├─ admin/           관리자 API /admin/v1/**  (별도 인증 필터)
│       ├─ domain/          엔티티 · 리포지토리 · 순수 도메인 규칙
│       ├─ batch/           재촉 · 체크포인트 · 알림 디스패치 스케줄러
│       └─ common/          보안 · 에러 · 설정
└─ infra/                   docker-compose
```

> 서비스 API와 관리자 API는 **필터 체인부터 분리**돼 있습니다. `/admin/v1/**` 은 관리자 토큰만
> 통과하고, 서비스 JWT로는 토큰 타입 검증에서 걸려 접근할 수 없습니다.

## 로컬 실행

```bash
# 1) DB · 캐시
docker compose -f infra/docker-compose.yml up -d

# 2) API 서버 (http://localhost:8080, 문서 /docs)
cd server && ./mvnw spring-boot:run
```

기동 시 로컬 프로필에서 표본 도서 4권과 관리자 계정이 시드됩니다.

| 항목 | 값 |
|---|---|
| 관리자 | `admin@bookey.local` / `bookey-local-1234` (`ADMIN_SEED_PASSWORD` 로 변경) |
| API 문서 | http://localhost:8080/docs |
| OpenAPI | http://localhost:8080/openapi.json |
| Postgres | `localhost:55432` (bookey / bookey_local) |
| Redis | `localhost:56379` |

### 개발용 로그인

`prod` 프로필이 아니면 `DEV` provider 로 토큰 없이 로그인할 수 있습니다.

```bash
curl -X POST http://localhost:8080/api/v1/auth/social \
  -H 'Content-Type: application/json' \
  -d '{"provider":"DEV","token":"tester1","nickname":"테스터"}'
```

## 프론트와의 계약

두 프론트 저장소는 `/openapi.json` 에서 TypeScript 타입을 생성해 씁니다.
**응답 스키마를 바꾸면 양쪽에서 `npm run types` 를 다시 돌려야 합니다.**

저장소가 나뉘어 있으니, 계약을 깨는 변경은 세 저장소에 각각 PR이 필요합니다.
호환되지 않는 변경은 필드를 지우기 전에 새 필드를 먼저 추가하는 식으로 단계를 나누세요.

## 환경 변수

| 키 | 설명 |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL 접속 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 접속 |
| `JWT_SECRET` | 32바이트 이상. **운영에서는 반드시 교체** |
| `KAKAO_REST_KEY` | 카카오 책 검색 (1차 검색) |
| `ALADIN_TTB_KEY` | 알라딘 OpenAPI (페이지 수 보강) |
| `GOOGLE_BOOKS_KEY` | Google Books (해외서 폴백, 선택) |

키가 없으면 해당 프로바이더를 건너뛰고 내부 캐시로만 검색합니다 (graceful degradation).

## 테스트

```bash
cd server && ./mvnw test
```

## GCP 배포

GitHub Actions가 `main` 브랜치 푸시 또는 수동 실행 시 Docker 이미지를 빌드해 Artifact Registry에 푸시하고 Cloud Run의 `bookey-backend` 서비스에 배포합니다.

GitHub Repository Variables:

| 키 | 예시 |
|---|---|
| `GCP_PROJECT_ID` | `bookey-prod` |
| `GCP_REGION` | `asia-northeast3` |
| `GCP_ARTIFACT_REGISTRY_REPOSITORY` | `bookey` |
| `GCP_CLOUD_RUN_SERVICE` | `bookey-backend` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/123456789/locations/global/workloadIdentityPools/github/providers/github` |
| `GCP_SERVICE_ACCOUNT` | `github-cloud-run@bookey-prod.iam.gserviceaccount.com` |

GitHub Repository Secrets:

| 키 | 설명 |
|---|---|
| `DB_URL` | 운영 PostgreSQL JDBC URL |
| `DB_USER` | 운영 DB 사용자 |
| `DB_PASSWORD` | 운영 DB 비밀번호 |
| `REDIS_HOST` | 운영 Redis 호스트 |
| `REDIS_PORT` | 운영 Redis 포트 |
| `JWT_SECRET` | 32바이트 이상 운영 JWT secret |
| `KAKAO_REST_KEY` | 카카오 책 검색 API 키 |
| `ALADIN_TTB_KEY` | 알라딘 OpenAPI 키 |
| `GOOGLE_BOOKS_KEY` | Google Books API 키 |

Bookey 전체 Cloud Run 서비스:

| 서비스 | 저장소 | 역할 |
|---|---|---|
| `bookey-backend` | `project-bookey-backend` | 서비스 API + 관리자 API |
| `bookey-admin` | `project-bookey-admin` | 관리자 웹 |

모바일 앱(`project-bookey-app`)은 Cloud Run 서비스가 아니라 빌드된 앱에서 `bookey-backend` URL을 바라보도록 설정합니다.
