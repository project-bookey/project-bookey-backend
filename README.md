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

로컬에서는 이메일로 가입한 뒤 로그인합니다. 가입 응답에도 토큰이 바로 담기므로 첫 호출은 `signup` 하나로 끝납니다.
(예전의 `DEV` provider 소셜 로그인은 제거되었습니다 — `AuthProvider` 는 `APPLE`·`GOOGLE`·`KAKAO` 만.)

```bash
# 가입 (최초 1회, 비밀번호 8자 이상) → TokenResponse
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"tester1@dev.local","password":"password1234","nickname":"테스터"}'

# 로그인 → TokenResponse
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"tester1@dev.local","password":"password1234"}'
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
| `STORAGE_TYPE` | 독후감 사진 저장소 — `local`(기본) / `gcs`. `prod` 프로파일 기본값은 `gcs` |
| `STORAGE_LOCAL_DIR` | `local` 일 때 파일을 둘 디렉터리 (기본 `./uploads` → `server/uploads`) |
| `STORAGE_PUBLIC_BASE_URL` | `local` 일 때 사진 URL 의 origin (예: `http://192.168.0.10:8080`). 비우면 요청 origin |
| `GCS_BUCKET` | `gcs` 일 때 버킷 이름 |

키가 없으면 해당 프로바이더를 건너뛰고 내부 캐시로만 검색합니다 (graceful degradation).

독후감 사진은 `POST /api/v1/posts/images` (multipart, `file` 파트) 로 먼저 올리고 응답의 `id` 를 독후감 `imageIds` 에 넣어 붙입니다. JPG·PNG·WebP, 파일당 10MB, 글당 10장. 24시간 안에 글에 붙지 않은 사진은 매일 04:20(KST) 배치가 파일과 행을 지웁니다.

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

### 업로드 저장소 (운영 필수)

독후감 사진은 운영에서 **반드시 GCS** 에 저장한다. Cloud Run 컨테이너의 파일시스템은 쓰기가 되지만 인스턴스가 교체·확장될 때마다 사라지므로, 로컬 디스크 저장소로 뜨면 업로드는 성공해 놓고 나중에 사진이 없어지고 다른 인스턴스에서는 `/uploads/{key}` 가 404 가 된다.

그래서 `prod` 프로파일은 `bookey.storage.type` 기본값을 `gcs` 로 두고(`application-prod.yml`), 기동 시 `StorageConfigValidator` 가 아래를 확인해 어긋나면 **서버를 띄우지 않는다**(새 리비전이 못 뜨면 Cloud Run 은 이전 리비전을 계속 서빙한다).

| 확인 | 값 | 환경변수 |
|---|---|---|
| `bookey.storage.type` | `gcs` | `STORAGE_TYPE` (prod 기본값이 `gcs`) |
| `bookey.storage.gcs.bucket` | 비어 있으면 안 됨 | `GCS_BUCKET` |

`.github/workflows/deploy-cloud-run.yml` 의 `env_vars` 는 `env_vars_update_strategy: overwrite` 라 배포할 때마다 Cloud Run 의 환경변수를 통째로 덮어쓴다. **콘솔에서 손으로 넣은 값은 다음 배포에서 지워지므로** 워크플로의 `env_vars` 목록에 직접 넣어야 한다:

```yaml
            GCS_BUCKET=${{ vars.GCP_MEDIA_BUCKET }}
```

### GCS 준비 (운영)

버킷은 아직 없다. 운영에 올리기 전에 아래를 한 번 실행한다 (`<bucket>` 은 예: `bookey-media`, GitHub Variables 의 `GCP_MEDIA_BUCKET` 에도 같은 값을 넣는다):

```bash
gcloud storage buckets create gs://<bucket> --location=asia-northeast3 --uniform-bucket-level-access
gcloud storage buckets add-iam-policy-binding gs://<bucket> --member=allUsers --role=roles/storage.objectViewer
# Cloud Run 런타임 서비스계정에 roles/storage.objectAdmin
# .github/workflows/deploy-cloud-run.yml env_vars 에 STORAGE_TYPE=gcs, GCS_BUCKET=${{ vars.GCP_MEDIA_BUCKET }} 추가 (이번엔 워크플로 파일을 수정하지 않는다)
```

공개 버킷이라 비공개 글의 사진도 URL 을 알면 열린다 (UUID 키로만 방어) — 서명 URL 은 백로그.

Bookey 전체 Cloud Run 서비스:

| 서비스 | 저장소 | 역할 |
|---|---|---|
| `bookey-backend` | `project-bookey-backend` | 서비스 API + 관리자 API |
| `bookey-admin` | `project-bookey-admin` | 관리자 웹 |

모바일 앱(`project-bookey-app`)은 Cloud Run 서비스가 아니라 빌드된 앱에서 `bookey-backend` URL을 바라보도록 설정합니다.
