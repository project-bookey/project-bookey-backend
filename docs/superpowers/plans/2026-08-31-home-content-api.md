# 홈 콘텐츠 API (배너·인기·추천) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱 홈 리디자인이 소비할 배너·인기 도서·에디터 픽 API 3종(공개/사용자용)과 그 관리용 어드민 API를 추가한다.

**Architecture:** 기존 피처 레이어링(`domain/<feature>` 엔티티+리포지토리, `api/<feature>` 컨트롤러+서비스+`XxxDtos` 홀더)을 그대로 따른다. 배너는 새 피처 `banner`, 에디터 픽은 새 피처 `curation`, 인기/추천 조회는 기존 `book` 피처에 얹는다. 스키마는 Flyway `V5` 마이그레이션 하나로 추가한다(`ddl-auto: validate`이므로 엔티티와 정확히 일치해야 함).

**Tech Stack:** Spring Boot 4.1.1 · Java 21 · JPA + Flyway(PostgreSQL) · springdoc(OpenAPI) · JUnit5+AssertJ (순수 단위 테스트 — 이 저장소 관례상 Spring 컨텍스트 없는 테스트만 존재).

**설계 근거:** 앱 저장소의 스펙 `project-bookey-app/docs/superpowers/specs/2026-08-31-home-redesign-design.md` — API 계약 섹션.

## Global Constraints

- 작업 디렉터리는 `server/`. 테스트 명령: `cd server && ./mvnw test`.
- DTO 스키마 이름은 앱이 OpenAPI 코드젠으로 소비한다 — record 이름 `BannerView`, `PopularBookView`는 확정 후 변경 금지.
- 응답 계약(스펙 표 그대로): `GET /api/v1/banners` → `BannerView[]` (인증 불필요), `GET /api/v1/books/popular?size=20` → `PopularBookView[]`, `GET /api/v1/books/recommended?size=20` → `BookSummary[]` (둘은 USER 인증).
- 어드민 관리 API는 `/admin/v1/**` 체인 — 권한은 기존 관례대로 컨트롤러에서 `admin.role().canManageOps()` 수동 체크, 실패 시 `ApiException.of(ErrorCode.ADMIN_FORBIDDEN)`.
- `@Operation(summary)` · `@DisplayName` · 주석은 한국어. 커밋 메시지는 짧은 명령형(이 저장소 관례, 접두사 없음) + 끝에 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` 푸터.
- 이 저장소에는 MockMvc/@SpringBootTest 선례가 없다 — 컨트롤러 테스트는 만들지 않고, 순수 로직(기간 필터·정렬·조립)을 단위 테스트한다. 최종 태스크에서 서버 기동 스모크로 라우팅을 검증한다.
- 엔티티 애너테이션 세트(@Getter/@Builder 등)는 `domain/reading/ReadingRecord.java`의 실제 스타일과 일치시킨다 — 아래 코드와 다르면 기존 스타일이 우선.

---

### Task 1: Flyway V5 + Banner·EditorPick 엔티티·리포지토리

**Files:**
- Create: `server/src/main/resources/db/migration/V5__home_content.sql`
- Create: `server/src/main/java/app/bookey/domain/banner/Banner.java`
- Create: `server/src/main/java/app/bookey/domain/banner/BannerRepository.java`
- Create: `server/src/main/java/app/bookey/domain/curation/EditorPick.java`
- Create: `server/src/main/java/app/bookey/domain/curation/EditorPickRepository.java`
- Test: `server/src/test/java/app/bookey/domain/banner/BannerTest.java`

**Interfaces:**
- Consumes: `BaseTimeEntity` (기존 공통 엔티티 베이스)
- Produces: `Banner`(엔티티, `isActiveAt(Instant)` 도메인 메서드), `BannerRepository.findAllByEnabledTrueOrderBySortOrderAscIdAsc()`, `EditorPick`(엔티티), `EditorPickRepository.findAllByOrderBySortOrderAscIdAsc()`, `existsByBookId(Long)`

- [ ] **Step 1: 실패하는 테스트 작성** — 배너 활성 기간 판정

```java
package app.bookey.domain.banner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BannerTest {

    private Banner banner(Instant startsAt, Instant endsAt) {
        return Banner.builder()
                .title("가을 독서 챌린지")
                .sortOrder(0)
                .enabled(true)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }

    @Test
    @DisplayName("시작 시각은 포함, 종료 시각은 제외한다")
    void activeWindowIsHalfOpen() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-09-21T00:00:00Z");
        Banner b = banner(start, end);

        assertThat(b.isActiveAt(start)).isTrue();
        assertThat(b.isActiveAt(end.minusSeconds(1))).isTrue();
        assertThat(b.isActiveAt(end)).isFalse();
        assertThat(b.isActiveAt(start.minusSeconds(1))).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd server && ./mvnw test -Dtest=BannerTest`
Expected: 컴파일 오류 (`Banner` 클래스 없음) — 실패 확인.

- [ ] **Step 3: 마이그레이션 + 엔티티 + 리포지토리 작성**

`V5__home_content.sql`:

```sql
-- 홈 콘텐츠: 이벤트 배너, 에디터 픽 (스펙: app 저장소 2026-08-31-home-redesign-design.md)
CREATE TABLE banners (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    subtitle VARCHAR(200),
    image_url VARCHAR(500),
    bg_color VARCHAR(20),   -- 이미지 없을 때 단색 배경 (#RRGGBB)
    link_url VARCHAR(500),  -- http(s)=외부, 그 외=앱 내 라우트
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE editor_picks (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id),
    sort_order INT NOT NULL DEFAULT 0,
    note VARCHAR(200),      -- 운영 메모 (노출 안 함)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_editor_picks_book ON editor_picks(book_id);

-- 인기 집계(book_id 기준 GROUP BY)용
CREATE INDEX IF NOT EXISTS ix_reading_records_book ON reading_records(book_id);
```

`Banner.java`:

```java
package app.bookey.domain.banner;

import app.bookey.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** 홈 이벤트 배너. 기간(startsAt~endsAt) 안에서만 노출한다. */
@Entity
@Table(name = "banners")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Banner extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "bg_color", length = 20)
    private String bgColor;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** 시작 포함, 종료 제외의 반열린 구간. */
    public boolean isActiveAt(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    public void update(String title, String subtitle, String imageUrl, String bgColor,
                       String linkUrl, int sortOrder, boolean enabled, Instant startsAt, Instant endsAt) {
        this.title = title;
        this.subtitle = subtitle;
        this.imageUrl = imageUrl;
        this.bgColor = bgColor;
        this.linkUrl = linkUrl;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}
```

`BannerRepository.java`:

```java
package app.bookey.domain.banner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    List<Banner> findAllByOrderBySortOrderAscIdAsc();
}
```

`EditorPick.java`:

```java
package app.bookey.domain.curation;

import app.bookey.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 에디터 픽 — 운영자가 고른 추천 도서. book_id 당 1건. */
@Entity
@Table(name = "editor_picks")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EditorPick extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 200)
    private String note;

    public void update(int sortOrder, String note) {
        this.sortOrder = sortOrder;
        this.note = note;
    }
}
```

`EditorPickRepository.java`:

```java
package app.bookey.domain.curation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EditorPickRepository extends JpaRepository<EditorPick, Long> {
    List<EditorPick> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByBookId(Long bookId);
}
```

주의: `BaseTimeEntity`의 실제 패키지 경로는 저장소에서 확인해 import를 맞춘다 (`ReadingRecord.java`의 import 참조).

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd server && ./mvnw test -Dtest=BannerTest`
Expected: PASS.

- [ ] **Step 5: 전체 테스트 + 커밋**

Run: `cd server && ./mvnw test`
Expected: 전체 PASS (기존 4개 + 신규 1개).

```bash
git add server/src/main/resources/db/migration/V5__home_content.sql server/src/main/java/app/bookey/domain/banner server/src/main/java/app/bookey/domain/curation server/src/test/java/app/bookey/domain/banner
git commit -m "홈 콘텐츠 스키마·엔티티 추가 (배너, 에디터 픽)"
```

---

### Task 2: 배너 공개 API

**Files:**
- Create: `server/src/main/java/app/bookey/api/banner/dto/BannerDtos.java`
- Create: `server/src/main/java/app/bookey/api/banner/BannerService.java`
- Create: `server/src/main/java/app/bookey/api/banner/BannerController.java`
- Modify: `server/src/main/java/app/bookey/common/config/SecurityConfig.java` (apiFilterChain의 permitAll 블록)
- Test: `server/src/test/java/app/bookey/api/banner/BannerServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 `Banner`, `BannerRepository.findAllByEnabledTrueOrderBySortOrderAscIdAsc()`
- Produces: `BannerDtos.BannerView(Long id, String title, String subtitle, String imageUrl, String bgColor, String linkUrl, int sortOrder)` + `BannerView.from(Banner)`, `BannerService.activeBanners()`, `BannerService.activeAt(List<Banner>, Instant)` (정적, 테스트용), `GET /api/v1/banners` (인증 불필요)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerView;
import app.bookey.domain.banner.Banner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BannerServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    private Banner banner(String title, Instant startsAt, Instant endsAt) {
        return Banner.builder()
                .title(title).sortOrder(0).enabled(true)
                .startsAt(startsAt).endsAt(endsAt)
                .build();
    }

    @Test
    @DisplayName("기간 밖 배너는 걸러지고, 입력 순서(정렬)는 유지된다")
    void filtersInactiveKeepsOrder() {
        Banner past = banner("지난", NOW.minusSeconds(200), NOW.minusSeconds(100));
        Banner first = banner("첫째", NOW.minusSeconds(100), NOW.plusSeconds(100));
        Banner second = banner("둘째", NOW.minusSeconds(100), NOW.plusSeconds(100));
        Banner future = banner("예정", NOW.plusSeconds(100), NOW.plusSeconds(200));

        List<BannerView> views = BannerService.activeAt(List.of(past, first, second, future), NOW);

        assertThat(views).extracting(BannerView::title).containsExactly("첫째", "둘째");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd server && ./mvnw test -Dtest=BannerServiceTest`
Expected: 컴파일 오류 (`BannerService`, `BannerDtos` 없음).

- [ ] **Step 3: 구현**

`BannerDtos.java`:

```java
package app.bookey.api.banner.dto;

import app.bookey.domain.banner.Banner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class BannerDtos {
    private BannerDtos() {}

    /** 앱 홈에 노출되는 활성 배너. */
    public record BannerView(
            @NotNull Long id,
            @NotNull String title,
            String subtitle,
            String imageUrl,
            String bgColor,
            String linkUrl,
            int sortOrder
    ) {
        public static BannerView from(Banner b) {
            return new BannerView(b.getId(), b.getTitle(), b.getSubtitle(),
                    b.getImageUrl(), b.getBgColor(), b.getLinkUrl(), b.getSortOrder());
        }
    }

    /** 어드민 조회용 — 기간·활성 여부 포함. */
    public record BannerAdminView(
            @NotNull Long id,
            @NotNull String title,
            String subtitle,
            String imageUrl,
            String bgColor,
            String linkUrl,
            int sortOrder,
            boolean enabled,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {
        public static BannerAdminView from(Banner b) {
            return new BannerAdminView(b.getId(), b.getTitle(), b.getSubtitle(), b.getImageUrl(),
                    b.getBgColor(), b.getLinkUrl(), b.getSortOrder(), b.isEnabled(),
                    b.getStartsAt(), b.getEndsAt());
        }
    }

    /** 어드민 생성/수정 요청 — 전체 필드 교체. */
    public record BannerUpsertRequest(
            @NotBlank String title,
            String subtitle,
            String imageUrl,
            String bgColor,
            String linkUrl,
            int sortOrder,
            boolean enabled,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {}
}
```

`BannerService.java`:

```java
package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerView;
import app.bookey.domain.banner.Banner;
import app.bookey.domain.banner.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository bannerRepository;

    @Transactional(readOnly = true)
    public List<BannerView> activeBanners() {
        return activeAt(bannerRepository.findAllByEnabledTrueOrderBySortOrderAscIdAsc(), Instant.now());
    }

    /** 기간 필터만 담당 — 정렬·enabled 필터는 쿼리가 이미 보장한다. */
    static List<BannerView> activeAt(List<Banner> banners, Instant now) {
        return banners.stream()
                .filter(b -> b.isActiveAt(now))
                .map(BannerView::from)
                .toList();
    }
}
```

`BannerController.java`:

```java
package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Banner", description = "홈 이벤트 배너")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "활성 배너 목록 — 기간 내, 정렬 순")
    @GetMapping
    public List<BannerView> list() {
        return bannerService.activeBanners();
    }
}
```

`SecurityConfig.java` — apiFilterChain의 permitAll 블록에 한 줄 추가 (기존 `/api/v1/public/**` 라인 근처):

```java
.requestMatchers(HttpMethod.GET, "/api/v1/banners").permitAll()
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd server && ./mvnw test -Dtest=BannerServiceTest`
Expected: PASS.

- [ ] **Step 5: 전체 테스트 + 커밋**

Run: `cd server && ./mvnw test`
Expected: 전체 PASS.

```bash
git add server/src/main/java/app/bookey/api/banner server/src/main/java/app/bookey/common/config/SecurityConfig.java server/src/test/java/app/bookey/api/banner
git commit -m "배너 공개 API 추가 (GET /api/v1/banners)"
```

---

### Task 3: 인기·추천 도서 API

**Files:**
- Modify: `server/src/main/java/app/bookey/domain/reading/ReadingRecordRepository.java` (집계 쿼리 추가)
- Modify: `server/src/main/java/app/bookey/api/book/dto/BookDtos.java` (`PopularBookView` 추가)
- Modify: `server/src/main/java/app/bookey/api/book/BookService.java` (popular/recommended 메서드)
- Modify: `server/src/main/java/app/bookey/api/book/BookController.java` (엔드포인트 2개)
- Test: `server/src/test/java/app/bookey/api/book/HomeContentAssemblerTest.java`

**Interfaces:**
- Consumes: Task 1의 `EditorPickRepository.findAllByOrderBySortOrderAscIdAsc()`, 기존 `Book` 엔티티·`BookRepository`·`BookSummary.from(Book)`
- Produces: `BookDtos.PopularBookView(BookSummary book, long savedCount)`, `ReadingRecordRepository.countSavedPerBook(Pageable)` + 프로젝션 `BookSavedCount { Long getBookId(); long getSavedCount(); }`, `BookService.popular(int size)` / `recommended(int size)` / 정적 `assemblePopular` / `assembleRecommended`, `GET /api/v1/books/popular` · `GET /api/v1/books/recommended`

- [ ] **Step 1: 실패하는 테스트 작성** — 조립 로직 (카운트 순서 유지, 삭제된 책 스킵)

```java
package app.bookey.api.book;

import app.bookey.api.book.dto.BookDtos.PopularBookView;
import app.bookey.domain.book.Book;
import app.bookey.domain.reading.ReadingRecordRepository.BookSavedCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HomeContentAssemblerTest {

    private record Count(Long bookId, long savedCount) implements BookSavedCount {
        @Override public Long getBookId() { return bookId; }
        @Override public long getSavedCount() { return savedCount; }
    }

    // Book 엔티티 생성은 기존 테스트 관례를 따른다 — 빌더가 없으면 ReadingSessionTest 등이 쓰는 생성 방식을 참조.
    private Book book(long id, String title) {
        return Book.builder().id(id).title(title).source(app.bookey.domain.book.BookSource.MANUAL).build();
    }

    @Test
    @DisplayName("집계 순서를 유지하고, 책이 없는 항목은 건너뛴다")
    void preservesOrderAndSkipsMissing() {
        List<BookSavedCount> counts = List.of(new Count(3L, 10), new Count(9L, 7), new Count(1L, 5));
        Map<Long, Book> books = Map.of(3L, book(3L, "첫째"), 1L, book(1L, "셋째")); // 9번 책은 없음

        List<PopularBookView> views = BookService.assemblePopular(counts, books);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).book().title()).isEqualTo("첫째");
        assertThat(views.get(0).savedCount()).isEqualTo(10);
        assertThat(views.get(1).book().title()).isEqualTo("셋째");
    }
}
```

주: `Book.builder()`가 없거나 필수 필드가 다르면 컴파일 단계에서 드러난다 — 그때는 기존 도메인 테스트(`ProgressCalculatorTest` 등)가 엔티티를 만드는 방식을 그대로 따르고, 테스트의 의도(순서 유지·누락 스킵)는 유지한다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd server && ./mvnw test -Dtest=HomeContentAssemblerTest`
Expected: 컴파일 오류 (`BookSavedCount`, `PopularBookView`, `assemblePopular` 없음).

- [ ] **Step 3: 구현**

`ReadingRecordRepository.java`에 추가:

```java
/** 책별 서재 담김 수 — 같은 사용자의 회차 반복은 1로 센다. */
@Query("""
        SELECT r.bookId AS bookId, COUNT(DISTINCT r.userId) AS savedCount
        FROM ReadingRecord r
        GROUP BY r.bookId
        ORDER BY COUNT(DISTINCT r.userId) DESC, r.bookId ASC
        """)
List<BookSavedCount> countSavedPerBook(Pageable pageable);

interface BookSavedCount {
    Long getBookId();
    long getSavedCount();
}
```

`BookDtos.java`에 추가 (홀더 클래스 안):

```java
/** 인기 도서 — 서재에 담긴 수 순. */
public record PopularBookView(@NotNull BookSummary book, long savedCount) {}
```

`BookService.java`에 추가 (필드 `ReadingRecordRepository readingRecordRepository`, `EditorPickRepository editorPickRepository` 주입 추가):

```java
@Transactional(readOnly = true)
public List<PopularBookView> popular(int size) {
    List<BookSavedCount> counts = readingRecordRepository.countSavedPerBook(PageRequest.of(0, size));
    Map<Long, Book> books = bookRepository.findAllById(
                    counts.stream().map(BookSavedCount::getBookId).toList())
            .stream().collect(Collectors.toMap(Book::getId, b -> b));
    return assemblePopular(counts, books);
}

static List<PopularBookView> assemblePopular(List<BookSavedCount> counts, Map<Long, Book> books) {
    return counts.stream()
            .filter(c -> books.containsKey(c.getBookId()))
            .map(c -> new PopularBookView(BookSummary.from(books.get(c.getBookId())), c.getSavedCount()))
            .toList();
}

@Transactional(readOnly = true)
public List<BookSummary> recommended(int size) {
    List<EditorPick> picks = editorPickRepository.findAllByOrderBySortOrderAscIdAsc();
    Map<Long, Book> books = bookRepository.findAllById(
                    picks.stream().map(EditorPick::getBookId).toList())
            .stream().collect(Collectors.toMap(Book::getId, b -> b));
    return assembleRecommended(picks.stream().limit(size).toList(), books);
}

static List<BookSummary> assembleRecommended(List<EditorPick> picks, Map<Long, Book> books) {
    return picks.stream()
            .filter(p -> books.containsKey(p.getBookId()))
            .map(p -> BookSummary.from(books.get(p.getBookId())))
            .toList();
}
```

`BookController.java`에 추가 (기존 `/{bookId}` 매핑보다 리터럴 경로가 우선 매칭되므로 충돌 없음):

```java
@Operation(summary = "인기 도서 — 서재에 담긴 수 순")
@GetMapping("/popular")
public List<PopularBookView> popular(@RequestParam(defaultValue = "20") int size) {
    return bookService.popular(size);
}

@Operation(summary = "추천 도서 — 에디터 픽")
@GetMapping("/recommended")
public List<BookSummary> recommended(@RequestParam(defaultValue = "20") int size) {
    return bookService.recommended(size);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd server && ./mvnw test -Dtest=HomeContentAssemblerTest`
Expected: PASS.

- [ ] **Step 5: 전체 테스트 + 커밋**

Run: `cd server && ./mvnw test`
Expected: 전체 PASS.

```bash
git add server/src/main/java/app/bookey/domain/reading/ReadingRecordRepository.java server/src/main/java/app/bookey/api/book server/src/test/java/app/bookey/api/book
git commit -m "인기·추천 도서 API 추가"
```

---

### Task 4: 어드민 관리 API (배너 CRUD · 에디터 픽 CRUD)

**Files:**
- Create: `server/src/main/java/app/bookey/api/banner/BannerAdminController.java`
- Create: `server/src/main/java/app/bookey/api/curation/dto/CurationDtos.java`
- Create: `server/src/main/java/app/bookey/api/curation/EditorPickAdminController.java`
- Create: `server/src/main/java/app/bookey/api/curation/EditorPickAdminService.java`
- Modify: `server/src/main/java/app/bookey/api/banner/BannerService.java` (어드민 메서드 추가)
- Modify: `server/src/main/java/app/bookey/common/error/ErrorCode.java` (2개 추가)

**Interfaces:**
- Consumes: Task 1~3의 엔티티·리포지토리·DTO, 기존 `AuthAdmin`(principal), `ApiException`/`ErrorCode`, `BookRepository`
- Produces: `/admin/v1/banners` GET·POST·PUT `/{id}`·DELETE `/{id}`, `/admin/v1/editor-picks` GET·POST·PATCH `/{id}`·DELETE `/{id}`, `ErrorCode.BANNER_NOT_FOUND`, `ErrorCode.EDITOR_PICK_DUPLICATE`

- [ ] **Step 1: ErrorCode 추가**

`ErrorCode.java`에 기존 항목 스타일대로 추가 (HttpStatus·한국어 메시지 형식은 기존 항목을 그대로 따른다):

```java
BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "배너를 찾을 수 없습니다."),
EDITOR_PICK_DUPLICATE(HttpStatus.CONFLICT, "이미 추천 목록에 있는 책입니다."),
```

- [ ] **Step 2: 배너 어드민 구현**

`BannerService.java`에 추가:

```java
@Transactional(readOnly = true)
public List<BannerDtos.BannerAdminView> adminList() {
    return bannerRepository.findAllByOrderBySortOrderAscIdAsc().stream()
            .map(BannerDtos.BannerAdminView::from).toList();
}

@Transactional
public BannerDtos.BannerAdminView create(BannerDtos.BannerUpsertRequest req) {
    Banner banner = Banner.builder()
            .title(req.title()).subtitle(req.subtitle()).imageUrl(req.imageUrl())
            .bgColor(req.bgColor()).linkUrl(req.linkUrl()).sortOrder(req.sortOrder())
            .enabled(req.enabled()).startsAt(req.startsAt()).endsAt(req.endsAt())
            .build();
    return BannerDtos.BannerAdminView.from(bannerRepository.save(banner));
}

@Transactional
public BannerDtos.BannerAdminView update(Long id, BannerDtos.BannerUpsertRequest req) {
    Banner banner = bannerRepository.findById(id)
            .orElseThrow(() -> ApiException.of(ErrorCode.BANNER_NOT_FOUND));
    banner.update(req.title(), req.subtitle(), req.imageUrl(), req.bgColor(),
            req.linkUrl(), req.sortOrder(), req.enabled(), req.startsAt(), req.endsAt());
    return BannerDtos.BannerAdminView.from(banner);
}

@Transactional
public void delete(Long id) {
    Banner banner = bannerRepository.findById(id)
            .orElseThrow(() -> ApiException.of(ErrorCode.BANNER_NOT_FOUND));
    bannerRepository.delete(banner);
}
```

`BannerAdminController.java`:

```java
package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerAdminView;
import app.bookey.api.banner.dto.BannerDtos.BannerUpsertRequest;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Banner", description = "배너 관리")
@RestController
@RequestMapping("/admin/v1/banners")
@RequiredArgsConstructor
public class BannerAdminController {

    private final BannerService bannerService;

    @Operation(summary = "배너 전체 목록 — 비활성·기간 외 포함")
    @GetMapping
    public List<BannerAdminView> list(@AuthenticationPrincipal AuthAdmin admin) {
        requireOps(admin);
        return bannerService.adminList();
    }

    @Operation(summary = "배너 생성")
    @PostMapping
    public BannerAdminView create(@AuthenticationPrincipal AuthAdmin admin,
                                  @Valid @RequestBody BannerUpsertRequest request) {
        requireOps(admin);
        return bannerService.create(request);
    }

    @Operation(summary = "배너 수정 — 전체 필드 교체")
    @PutMapping("/{id}")
    public BannerAdminView update(@AuthenticationPrincipal AuthAdmin admin,
                                  @PathVariable Long id,
                                  @Valid @RequestBody BannerUpsertRequest request) {
        requireOps(admin);
        return bannerService.update(id, request);
    }

    @Operation(summary = "배너 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        requireOps(admin);
        bannerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireOps(AuthAdmin admin) {
        if (!admin.role().canManageOps()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
```

- [ ] **Step 3: 에디터 픽 어드민 구현**

`CurationDtos.java`:

```java
package app.bookey.api.curation.dto;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.domain.curation.EditorPick;
import jakarta.validation.constraints.NotNull;

public final class CurationDtos {
    private CurationDtos() {}

    public record EditorPickView(
            @NotNull Long id,
            @NotNull BookSummary book,
            int sortOrder,
            String note
    ) {}

    public record EditorPickCreateRequest(@NotNull Long bookId, int sortOrder, String note) {}

    public record EditorPickUpdateRequest(int sortOrder, String note) {}
}
```

주: `BookSummary`의 실제 패키지가 `api.book.dto.BookDtos` 홀더 내부이므로 import 경로는 실제 선언에 맞춘다.

`EditorPickAdminService.java`:

```java
package app.bookey.api.curation;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.api.curation.dto.CurationDtos.EditorPickCreateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickUpdateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.curation.EditorPick;
import app.bookey.domain.curation.EditorPickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EditorPickAdminService {

    private final EditorPickRepository editorPickRepository;
    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public List<EditorPickView> list() {
        List<EditorPick> picks = editorPickRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Book> books = bookRepository.findAllById(
                        picks.stream().map(EditorPick::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, b -> b));
        return picks.stream()
                .filter(p -> books.containsKey(p.getBookId()))
                .map(p -> new EditorPickView(p.getId(), BookSummary.from(books.get(p.getBookId())),
                        p.getSortOrder(), p.getNote()))
                .toList();
    }

    @Transactional
    public EditorPickView create(EditorPickCreateRequest req) {
        Book book = bookRepository.findById(req.bookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        if (editorPickRepository.existsByBookId(req.bookId())) {
            throw ApiException.of(ErrorCode.EDITOR_PICK_DUPLICATE);
        }
        EditorPick pick = EditorPick.builder()
                .bookId(req.bookId()).sortOrder(req.sortOrder()).note(req.note())
                .build();
        pick = editorPickRepository.save(pick);
        return new EditorPickView(pick.getId(), BookSummary.from(book), pick.getSortOrder(), pick.getNote());
    }

    @Transactional
    public EditorPickView update(Long id, EditorPickUpdateRequest req) {
        EditorPick pick = editorPickRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
        pick.update(req.sortOrder(), req.note());
        Book book = bookRepository.findById(pick.getBookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        return new EditorPickView(pick.getId(), BookSummary.from(book), pick.getSortOrder(), pick.getNote());
    }

    @Transactional
    public void delete(Long id) {
        EditorPick pick = editorPickRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
        editorPickRepository.delete(pick);
    }
}
```

주: `RECORD_NOT_FOUND`가 서재 레코드 전용 의미라면 `EDITOR_PICK_NOT_FOUND`를 ErrorCode에 추가해 대체한다 — 기존 enum의 의미를 확인하고 결정.

`EditorPickAdminController.java`:

```java
package app.bookey.api.curation;

import app.bookey.api.curation.dto.CurationDtos.EditorPickCreateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickUpdateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin EditorPick", description = "에디터 픽(추천 도서) 관리")
@RestController
@RequestMapping("/admin/v1/editor-picks")
@RequiredArgsConstructor
public class EditorPickAdminController {

    private final EditorPickAdminService editorPickAdminService;

    @Operation(summary = "에디터 픽 목록")
    @GetMapping
    public List<EditorPickView> list(@AuthenticationPrincipal AuthAdmin admin) {
        requireOps(admin);
        return editorPickAdminService.list();
    }

    @Operation(summary = "에디터 픽 추가")
    @PostMapping
    public EditorPickView create(@AuthenticationPrincipal AuthAdmin admin,
                                 @Valid @RequestBody EditorPickCreateRequest request) {
        requireOps(admin);
        return editorPickAdminService.create(request);
    }

    @Operation(summary = "에디터 픽 수정 — 정렬·메모")
    @PatchMapping("/{id}")
    public EditorPickView update(@AuthenticationPrincipal AuthAdmin admin,
                                 @PathVariable Long id,
                                 @Valid @RequestBody EditorPickUpdateRequest request) {
        requireOps(admin);
        return editorPickAdminService.update(id, request);
    }

    @Operation(summary = "에디터 픽 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        requireOps(admin);
        editorPickAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireOps(AuthAdmin admin) {
        if (!admin.role().canManageOps()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
```

- [ ] **Step 4: 전체 테스트 + 커밋**

Run: `cd server && ./mvnw test`
Expected: 전체 PASS (신규 로직은 순수 조립·CRUD라 기존 단위 테스트 대상 아님 — 라우팅·권한은 Task 5 스모크에서 확인).

```bash
git add server/src/main/java/app/bookey/api/banner server/src/main/java/app/bookey/api/curation server/src/main/java/app/bookey/common/error/ErrorCode.java
git commit -m "배너·에디터 픽 어드민 관리 API 추가"
```

---

### Task 5: 기동 스모크 검증

**Files:** 없음 (검증 전용)

**Interfaces:**
- Consumes: Task 1~4 전부
- Produces: 앱 저장소가 `npm run types`를 돌릴 수 있는 상태 (OpenAPI에 신규 스키마 노출)

- [ ] **Step 1: 서버 기동**

Run: `cd server && ./mvnw spring-boot:run` (백그라운드). 기동 로그에서 Flyway `V5__home_content` 적용과 `ddl-auto: validate` 통과를 확인. 이미 다른 서버가 8080에 떠 있으면 먼저 내린다.

- [ ] **Step 2: 엔드포인트 스모크**

```bash
curl -s http://localhost:8080/api/v1/banners            # 기대: 200, []
curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/books/popular      # 기대: 401 (인증 필요 = 라우트 존재)
curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/books/recommended  # 기대: 401
curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/admin/v1/banners          # 기대: 401 (어드민 체인)
curl -s http://localhost:8080/openapi.json | grep -c 'BannerView\|PopularBookView'     # 기대: 1 이상
```

- [ ] **Step 3: 커밋 확인**

작업 트리가 깨끗한지 확인(`git status`). 추가 변경이 생겼다면 원인을 파악하고 해당 태스크 커밋에 포함시킨다.
