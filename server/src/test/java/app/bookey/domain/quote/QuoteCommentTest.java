package app.bookey.domain.quote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteCommentTest {

    private QuoteComment comment(Long quoteId, Long userId) {
        return QuoteComment.builder().quoteId(quoteId).userId(userId).body("덧붙임").build();
    }

    @Test
    @DisplayName("작성자 본인이면 true, 아니면 false를 반환한다")
    void isOwnedByChecksAuthor() {
        QuoteComment comment = comment(1L, 10L);

        assertThat(comment.isOwnedBy(10L)).isTrue();
        assertThat(comment.isOwnedBy(99L)).isFalse();
    }

    @Test
    @DisplayName("경로의 밑줄에 달린 댓글인지 확인한다")
    void belongsToChecksQuote() {
        QuoteComment comment = comment(1L, 10L);

        assertThat(comment.belongsTo(1L)).isTrue();
        assertThat(comment.belongsTo(2L)).isFalse();
    }
}
