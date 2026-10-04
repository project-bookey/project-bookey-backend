package app.bookey.api.plaza;

import app.bookey.api.plaza.dto.PlazaDtos.PlazaItemView;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookSource;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.remark.BookRemark;
import app.bookey.domain.remark.RemarkKind;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaServiceTest {

    private ReadingRecord finishedRecord(long id, long userId, long bookId, Instant finishedAt) {
        ReadingRecord record = ReadingRecord.builder().userId(userId).bookId(bookId).build();
        record.finish(finishedAt, null);
        set(record, "id", id);
        return record;
    }

    // Book.builder()는 id(자동 생성 PK)를 받지 않는다 — 리플렉션으로 채운다.
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

    private BookRemark remark(long recordId, RemarkKind kind, String body) {
        return BookRemark.builder().userId(10L).bookId(100L).readingRecordId(recordId)
                .kind(kind).body(body).writtenAt(Instant.now()).build();
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

    // ────────────────────────────── feed(QUOTE) ──────────────────────────────

    @Test
    @DisplayName("feed: 밑줄(QUOTE)은 걷어냈다 — 옛 앱이 불러도 저장소를 건드리지 않고 빈 페이지를 준다")
    void quoteFeedIsAlwaysEmpty() {
        // 저장소 없이 만든다 — QUOTE 경로가 저장소를 부르면 NullPointerException 으로 드러난다.
        PlazaService service = new PlazaService(null, null, null, null);

        PageResponse<PlazaItemView> page = service.feed(PlazaItemType.QUOTE, PageRequest.of(2, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    // ────────────────────────────── assembleFinishItems ──────────────────────────────

    @Test
    @DisplayName("assembleFinishItems: occurredAt은 finishedAt이고 작성자·책을 채운다")
    void assembleFinishItemsOccurredAtIsFinishedAt() {
        Instant finishedAt = Instant.parse("2026-08-01T00:00:00Z");
        ReadingRecord record = finishedRecord(1L, 10L, 100L, finishedAt);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PlazaItemView> items = PlazaService.assembleFinishItems(List.of(record), books, authors, Map.of());

        PlazaItemView item = items.get(0);
        assertThat(item.type()).isEqualTo(PlazaItemType.FINISH);
        assertThat(item.occurredAt()).isEqualTo(finishedAt);
        assertThat(item.bookId()).isEqualTo(100L);
        assertThat(item.bookTitle()).isEqualTo("책");
        assertThat(item.authorId()).isEqualTo(10L);
        assertThat(item.authorNickname()).isEqualTo("작가");
    }

    @Test
    @DisplayName("assembleFinishItems: 탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void assembleFinishItemsWithdrawnAuthorFallsBackToUnknown() {
        ReadingRecord record = finishedRecord(1L, 99L, 100L, Instant.now());
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));

        List<PlazaItemView> items = PlazaService.assembleFinishItems(List.of(record), books, Map.of(), Map.of());

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
                List.of(withBook, withoutBook), books, authors, Map.of());

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

        List<PlazaItemView> items = PlazaService.assembleFinishItems(List.of(first, second), books, authors, Map.of());

        assertThat(items).extracting(PlazaItemView::occurredAt).containsExactly(firstAt, secondAt);
    }

    @Test
    @DisplayName("assembleFinishItems: 그 회차의 한 마디를 붙이고, 남기지 않은 회차는 비워 둔다")
    void assembleFinishItemsAttachesRemarkByRecord() {
        ReadingRecord withRemark = finishedRecord(1L, 10L, 100L, Instant.now());
        ReadingRecord bare = finishedRecord(2L, 10L, 100L, Instant.now());
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));
        Map<Long, String> remarks = Map.of(1L, "끝까지 읽길 잘했다");

        List<PlazaItemView> items = PlazaService.assembleFinishItems(
                List.of(withRemark, bare), books, authors, remarks);

        assertThat(items.get(0).remark()).isEqualTo("끝까지 읽길 잘했다");
        assertThat(items.get(1).remark()).isNull();
    }

    @Test
    @DisplayName("finishRemarks: 하차하며 남긴 한 마디는 완독 자랑에 붙이지 않는다")
    void finishRemarksDropsAbandoned() {
        Map<Long, String> remarks = PlazaService.finishRemarks(List.of(
                remark(1L, RemarkKind.FINISHED, "다 읽었다"),
                remark(2L, RemarkKind.ABANDONED, "여기까지")));

        assertThat(remarks).containsOnlyKeys(1L).containsEntry(1L, "다 읽었다");
    }
}
