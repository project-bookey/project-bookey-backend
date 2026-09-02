package app.bookey.api.plaza;

import app.bookey.api.plaza.dto.PlazaDtos.PlazaItemView;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookSource;
import app.bookey.domain.quote.BookQuote;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaServiceTest {

    private BookQuote quote(long id, long userId, long bookId) {
        BookQuote quote = BookQuote.builder()
                .userId(userId).bookId(bookId).content("문장 " + id).page(10)
                .build();
        set(quote, "id", id);
        return quote;
    }

    private ReadingRecord finishedRecord(long id, long userId, long bookId, Instant finishedAt) {
        ReadingRecord record = ReadingRecord.builder().userId(userId).bookId(bookId).build();
        record.finish(finishedAt, null);
        set(record, "id", id);
        return record;
    }

    // Book.builder()는 id(자동 생성 PK)를 받지 않는다 — QuoteServiceTest처럼 리플렉션으로 채운다.
    private Book book(long id, String title) {
        Book book = Book.builder().title(title).source(BookSource.MANUAL).build();
        set(book, "id", id);
        return book;
    }

    private User user(long id, String nickname) {
        User user = User.builder().handle("handle" + id).nickname(nickname).build();
        set(user, "id", id);
        return user;
    }

    private void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ────────────────────────────── assembleQuoteItems ──────────────────────────────

    @Test
    @DisplayName("assembleQuoteItems: agreeCount·agreedByMe를 배치 맵으로 매핑한다")
    void assembleQuoteItemsMapsAgreeCountAndAgreedByMe() {
        BookQuote quote = quote(1L, 10L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));
        Map<Long, Long> agreeCounts = Map.of(1L, 3L);
        Set<Long> myAgreed = Set.of(1L);

        List<PlazaItemView> items = PlazaService.assembleQuoteItems(
                List.of(quote), books, authors, agreeCounts, myAgreed, Set.of());

        PlazaItemView item = items.get(0);
        assertThat(item.type()).isEqualTo(PlazaItemType.QUOTE);
        assertThat(item.quoteId()).isEqualTo(1L);
        assertThat(item.content()).isEqualTo(quote.getContent());
        assertThat(item.page()).isEqualTo(10);
        assertThat(item.agreeCount()).isEqualTo(3L);
        assertThat(item.agreedByMe()).isTrue();
        assertThat(item.bookId()).isEqualTo(100L);
        assertThat(item.bookTitle()).isEqualTo("책");
    }

    @Test
    @DisplayName("assembleQuoteItems: 나도 그럼 카운트가 없는 문장은 0으로 매핑한다")
    void assembleQuoteItemsMissingAgreeCountDefaultsToZero() {
        BookQuote quote = quote(2L, 10L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PlazaItemView> items = PlazaService.assembleQuoteItems(
                List.of(quote), books, authors, Map.of(), Set.of(), Set.of());

        PlazaItemView item = items.get(0);
        assertThat(item.agreeCount()).isZero();
        assertThat(item.agreedByMe()).isFalse();
    }

    @Test
    @DisplayName("assembleQuoteItems: 입력 순서를 그대로 유지한다")
    void assembleQuoteItemsPreservesInputOrder() {
        BookQuote first = quote(5L, 10L, 100L);
        BookQuote second = quote(3L, 10L, 100L);
        BookQuote third = quote(9L, 10L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PlazaItemView> items = PlazaService.assembleQuoteItems(
                List.of(first, second, third), books, authors, Map.of(), Set.of(), Set.of());

        assertThat(items).extracting(PlazaItemView::quoteId).containsExactly(5L, 3L, 9L);
    }

    @Test
    @DisplayName("assembleQuoteItems: 탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void assembleQuoteItemsWithdrawnAuthorFallsBackToUnknown() {
        BookQuote quote = quote(2L, 99L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));

        List<PlazaItemView> items = PlazaService.assembleQuoteItems(
                List.of(quote), books, Map.of(), Map.of(), Set.of(), Set.of());

        PlazaItemView item = items.get(0);
        assertThat(item.authorNickname()).isEqualTo("알 수 없음");
        assertThat(item.authorAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("assembleQuoteItems: 책이 결측된 행은 필터한다")
    void assembleQuoteItemsFiltersRowsWithMissingBook() {
        BookQuote withBook = quote(1L, 10L, 100L);
        BookQuote withoutBook = quote(2L, 10L, 200L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PlazaItemView> items = PlazaService.assembleQuoteItems(
                List.of(withBook, withoutBook), books, authors, Map.of(), Set.of(), Set.of());

        assertThat(items).extracting(PlazaItemView::quoteId).containsExactly(1L);
    }

    @Test
    @DisplayName("assembleQuoteItems: 작성자가 그 책을 완독한 밑줄만 authorFinished 가 true 다")
    void assembleQuoteItemsMarksAuthorFinished() {
        BookQuote finishedOne = quote(1L, 10L, 100L);
        BookQuote notFinished = quote(2L, 10L, 200L);
        BookQuote otherAuthor = quote(3L, 20L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"), 200L, book(200L, "다른 책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"), 20L, user(20L, "이웃"));
        Set<PlazaService.UserBook> finished = Set.of(new PlazaService.UserBook(10L, 100L));

        List<PlazaItemView> items = PlazaService.assembleQuoteItems(
                List.of(finishedOne, notFinished, otherAuthor), books, authors, Map.of(), Set.of(), finished);

        assertThat(items).extracting(PlazaItemView::authorFinished).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("assembleFinishItems: 완독 자랑은 authorFinished 가 항상 true 다")
    void assembleFinishItemsAlwaysAuthorFinished() {
        ReadingRecord record = finishedRecord(1L, 10L, 100L, Instant.parse("2026-08-01T00:00:00Z"));

        List<PlazaItemView> items = PlazaService.assembleFinishItems(
                List.of(record), Map.of(100L, book(100L, "책")), Map.of(10L, user(10L, "작가")));

        assertThat(items.get(0).authorFinished()).isTrue();
    }

    // ────────────────────────────── assembleFinishItems ──────────────────────────────

    @Test
    @DisplayName("assembleFinishItems: occurredAt은 finishedAt이고 QUOTE 전용 필드는 전부 null이다")
    void assembleFinishItemsOccurredAtIsFinishedAtAndQuoteFieldsAreNull() {
        Instant finishedAt = Instant.parse("2026-08-01T00:00:00Z");
        ReadingRecord record = finishedRecord(1L, 10L, 100L, finishedAt);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PlazaItemView> items = PlazaService.assembleFinishItems(List.of(record), books, authors);

        PlazaItemView item = items.get(0);
        assertThat(item.type()).isEqualTo(PlazaItemType.FINISH);
        assertThat(item.occurredAt()).isEqualTo(finishedAt);
        assertThat(item.bookId()).isEqualTo(100L);
        assertThat(item.bookTitle()).isEqualTo("책");
        assertThat(item.authorId()).isEqualTo(10L);
        assertThat(item.authorNickname()).isEqualTo("작가");
        assertThat(item.quoteId()).isNull();
        assertThat(item.content()).isNull();
        assertThat(item.page()).isNull();
        assertThat(item.agreeCount()).isNull();
        assertThat(item.agreedByMe()).isNull();
    }

    @Test
    @DisplayName("assembleFinishItems: 탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void assembleFinishItemsWithdrawnAuthorFallsBackToUnknown() {
        ReadingRecord record = finishedRecord(1L, 99L, 100L, Instant.now());
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));

        List<PlazaItemView> items = PlazaService.assembleFinishItems(List.of(record), books, Map.of());

        assertThat(items.get(0).authorNickname()).isEqualTo("알 수 없음");
        assertThat(items.get(0).authorAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("assembleFinishItems: 책이 결측된 행은 필터한다")
    void assembleFinishItemsFiltersRowsWithMissingBook() {
        ReadingRecord withBook = finishedRecord(1L, 10L, 100L, Instant.now());
        ReadingRecord withoutBook = finishedRecord(2L, 10L, 200L, Instant.now());
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PlazaItemView> items = PlazaService.assembleFinishItems(
                List.of(withBook, withoutBook), books, authors);

        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("assembleFinishItems: 입력 순서를 그대로 유지한다")
    void assembleFinishItemsPreservesInputOrder() {
        Instant firstAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant secondAt = Instant.parse("2026-07-01T00:00:00Z");
        ReadingRecord first = finishedRecord(5L, 10L, 100L, firstAt);
        ReadingRecord second = finishedRecord(3L, 10L, 100L, secondAt);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PlazaItemView> items = PlazaService.assembleFinishItems(List.of(first, second), books, authors);

        assertThat(items).extracting(PlazaItemView::occurredAt).containsExactly(firstAt, secondAt);
    }
}
