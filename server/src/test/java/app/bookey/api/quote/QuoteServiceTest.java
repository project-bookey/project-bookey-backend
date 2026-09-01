package app.bookey.api.quote;

import app.bookey.api.quote.dto.QuoteDtos.BookQuoteView;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookSource;
import app.bookey.domain.quote.BookQuote;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteServiceTest {

    private BookQuote quote(long id, long userId, long bookId) {
        BookQuote quote = BookQuote.builder()
                .userId(userId).bookId(bookId).content("문장 " + id).page(10)
                .build();
        set(quote, "id", id);
        return quote;
    }

    // Book.builder()는 id(자동 생성 PK)를 받지 않는다 — HomeContentAssemblerTest처럼 리플렉션으로 채운다.
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

    @Test
    @DisplayName("agreeCount·agreedByMe·mine을 배치 맵으로 매핑한다")
    void mapsAgreeAndOwnership() {
        BookQuote quote = quote(1L, 10L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));
        Map<Long, Long> agreeCounts = Map.of(1L, 3L);
        Set<Long> myAgreed = Set.of(1L);

        List<BookQuoteView> views = QuoteService.assembleViews(
                List.of(quote), 10L, books, authors, agreeCounts, myAgreed);

        BookQuoteView view = views.get(0);
        assertThat(view.agreeCount()).isEqualTo(3L);
        assertThat(view.agreedByMe()).isTrue();
        assertThat(view.mine()).isTrue();
    }

    @Test
    @DisplayName("나도 그럼 카운트가 없는 문장은 0으로 매핑한다")
    void missingAgreeCountDefaultsToZero() {
        BookQuote quote = quote(2L, 10L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<BookQuoteView> views = QuoteService.assembleViews(
                List.of(quote), 20L, books, authors, Map.of(), Set.of());

        BookQuoteView view = views.get(0);
        assertThat(view.agreeCount()).isZero();
        assertThat(view.agreedByMe()).isFalse();
        assertThat(view.mine()).isFalse(); // 조회자(20)가 작성자(10)와 다름
    }

    @Test
    @DisplayName("탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void withdrawnAuthorFallsBackToUnknown() {
        BookQuote quote = quote(3L, 99L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));

        List<BookQuoteView> views = QuoteService.assembleViews(
                List.of(quote), 1L, books, Map.of(), Map.of(), Set.of());

        BookQuoteView view = views.get(0);
        assertThat(view.authorNickname()).isEqualTo("알 수 없음");
        assertThat(view.authorAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("입력 순서를 그대로 유지한다")
    void preservesInputOrder() {
        BookQuote first = quote(5L, 10L, 100L);
        BookQuote second = quote(3L, 10L, 100L);
        BookQuote third = quote(9L, 10L, 100L);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<BookQuoteView> views = QuoteService.assembleViews(
                List.of(first, second, third), 10L, books, authors, Map.of(), Set.of());

        assertThat(views).extracting(BookQuoteView::id).containsExactly(5L, 3L, 9L);
    }
}
