# 오려둔 문장(book quotes) + 광장(plaza) 피드 — 구현 계획

2026-09-01. 앱 저장소 콜라주 리디자인 스펙(`project-bookey-app/docs/superpowers/specs/2026-09-01-collage-redesign-design.md`)의 백엔드 파트.

## 범위

**포함**: 문장 작성/삭제/내 목록/책별 목록, 나도 그럼(agree) 토글, 광장 피드(밑줄 QUOTE·완독 자랑 FINISH).
**컷(백로그)**: 덧붙임(답글) · 오려두기(re-clip) · 문장 수정(PATCH) · 신고/모더레이션(additive V9 + AbuseReport 확장) · 완독 공유 옵트아웃 · agree 레이스 409 변환(좋아요 백로그에 합류). 토론 칩은 기존 `GET /api/v1/clubs/public` 재사용 — 신규 엔드포인트 없음. visibility 컬럼 없음(MVP 전부 공개).

## Task B1 — `feature/book-quotes`

### V8__book_quotes.sql

```sql
CREATE TABLE book_quotes (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id           BIGINT NOT NULL REFERENCES books(id),
    reading_record_id BIGINT REFERENCES reading_records(id) ON DELETE SET NULL,
    content           VARCHAR(500) NOT NULL,
    page              INT CHECK (page >= 1),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_book_quotes_user    ON book_quotes(user_id, created_at DESC);
CREATE INDEX idx_book_quotes_book    ON book_quotes(book_id, created_at DESC);
CREATE INDEX idx_book_quotes_created ON book_quotes(created_at DESC, id DESC);

CREATE TABLE quote_agrees (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quote_id   BIGINT NOT NULL REFERENCES book_quotes(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, quote_id)
);
CREATE INDEX idx_quote_agrees_quote ON quote_agrees(quote_id);

CREATE INDEX idx_reading_records_finished_feed
    ON reading_records(finished_at DESC) WHERE status = 'FINISHED';
```

### 도메인 — `domain/quote/`

- `BookQuote`(BaseTimeEntity, `@Builder`, userId/bookId/readingRecordId/content/page, `isOwnedBy(Long)`), `BookQuoteRepository`(`findAllByUserIdOrderByCreatedAtDescIdDesc` / `findAllByBookIdOrderByCreatedAtDescIdDesc` / `findAllByOrderByCreatedAtDescIdDesc`).
- `QuoteAgree`(BookLike 미러), `QuoteAgreeRepository`(`findByUserIdAndQuoteId` / `countByQuoteId` / `findAllByUserIdAndQuoteIdIn` / `countPerQuote` GROUP BY 프로젝션 `AgreeCount`).

### API — `api/quote/` + BookController 확장

| 메서드/경로 | 응답 | 비고 |
|---|---|---|
| POST `/api/v1/quotes` | `BookQuoteView` | RateLimiter `quote:create:{userId}` 10건/분 |
| GET `/api/v1/quotes?page&size` | `PageResponse<BookQuoteView>` | 내 목록 최신순 (totalElements = "내가 오려둔 문장 N") |
| DELETE `/api/v1/quotes/{quoteId}` | 204 | 본인만 — FORBIDDEN / 신규 `ErrorCode.QUOTE_NOT_FOUND` |
| POST `/api/v1/quotes/{quoteId}/agree` | `QuoteAgreeView` | book_likes toggleLike 미러 |
| GET `/api/v1/books/{bookId}/quotes?page&size` | `PageResponse<BookQuoteView>` | BookController에 추가, QuoteService 위임 |

DTO (`api/quote/dto/QuoteDtos.java` record 홀더, OpenApiRequiredFieldsConfig 규칙 — 선택 필드는 박싱):

```java
public record CreateBookQuoteRequest(@NotNull Long bookId, Long readingRecordId,
        @NotBlank @Size(max = 500) String content, @Min(1) Integer page) {}
public record BookQuoteView(@NotNull Long id, @NotNull Long bookId, @NotNull String bookTitle,
        String bookCoverUrl, Integer page, @NotNull String content,
        @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
        long agreeCount, boolean agreedByMe, boolean mine, @NotNull Instant createdAt) {}
public record QuoteAgreeView(boolean agreed, long agreeCount) {}
```

서비스: create(BOOK_NOT_FOUND / record 소유 확인 / RateLimiter) · delete(isOwnedBy) · toggleAgree(find→delete/save, countByQuoteId) · 목록은 배치 맵(books/users/agreeCounts/myAgrees) + package-private static `assembleViews(...)` (ClubPostService.loadAuthors·HomeContentAssembler 선례). 탈퇴 작성자 "알 수 없음" 폴백.

### 테스트 (순수 단위 — 컨텍스트/Mockito 없음, `@DisplayName` 한국어, TDD)

- `BookQuoteTest` — isOwnedBy 판정.
- `QuoteServiceTest` — assembleViews: agreeCount/agreedByMe/mine 매핑 · 카운트 결측 0 · 탈퇴 작성자 폴백 · 입력 순서 유지.

## Task B2 — `feature/plaza-feed`

- `ReadingRecordRepository`에 `findFinishFeed` 추가(status='FINISHED' AND finishedAt IS NOT NULL, finished_at DESC).
- `api/plaza/`: `PlazaItemType`(QUOTE, FINISH 톱레벨 enum), `PlazaDtos.PlazaItemView`(type·author 3필드·book 3필드·occurredAt + QUOTE 전용 박싱 `quoteId/content/page/agreeCount/agreedByMe`), `PlazaService`(assembleQuoteItems / assembleFinishItems — 책 결측 행 필터), `PlazaController` GET `/api/v1/plaza/feed?type=QUOTE&page&size` → `PageResponse<PlazaItemView>` (type 기본 QUOTE).
- 테스트 `PlazaServiceTest` — occurredAt=finishedAt · FINISH 아이템의 QUOTE 필드 null · 순서 유지 · 책 결측 필터.

## 검증

`cd server && JAVA_HOME='C:\Users\ANT010\.jdks\corretto-21.0.7' ./mvnw test` 전체 green. 각 태스크 머지 후 앱에서 `npm run types` 재생성(서버 기동 필요 — ddl validate가 V8·엔티티 일치 강제).

## 리스크

offset 페이지네이션 깊은 페이지(MVP 인덱스로 충분, 커서는 백로그) · N+1(페이지당 쿼리 5개 고정 배치 맵 필수) · OpenAPI 스키마명(`BookQuoteView` 등 — ClubPostType.QUOTE는 enum 값이라 충돌 없음, 배포 후 이름 변경 금지).
