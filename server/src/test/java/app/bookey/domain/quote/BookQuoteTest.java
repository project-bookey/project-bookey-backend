package app.bookey.domain.quote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookQuoteTest {

    private BookQuote quote(Long userId) {
        return BookQuote.builder()
                .userId(userId)
                .bookId(1L)
                .content("문장")
                .build();
    }

    @Test
    @DisplayName("작성자 본인이면 true, 아니면 false를 반환한다")
    void isOwnedByChecksAuthor() {
        BookQuote quote = quote(10L);

        assertThat(quote.isOwnedBy(10L)).isTrue();
        assertThat(quote.isOwnedBy(99L)).isFalse();
    }
}
