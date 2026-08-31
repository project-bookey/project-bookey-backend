package app.bookey.api.book;

import app.bookey.api.book.dto.BookDtos.PopularBookView;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookSource;
import app.bookey.domain.reading.ReadingRecordRepository.BookSavedCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HomeContentAssemblerTest {

    private record Count(Long bookId, long savedCount) implements BookSavedCount {
        @Override public Long getBookId() { return bookId; }
        @Override public long getSavedCount() { return savedCount; }
    }

    // Book.builder()는 id(자동 생성 PK)를 받지 않는다 — ProgressCalculatorTest처럼 리플렉션으로 채운다.
    private Book book(long id, String title) {
        Book book = Book.builder().title(title).source(BookSource.MANUAL).build();
        set(book, "id", id);
        return book;
    }

    private void set(Object target, String field, Object value) {
        try {
            Field f = Book.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
