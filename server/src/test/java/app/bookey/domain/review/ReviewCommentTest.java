package app.bookey.domain.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewCommentTest {

    private ReviewComment comment(Long reviewId, Long userId) {
        return ReviewComment.builder().reviewId(reviewId).userId(userId).body("덧붙임").build();
    }

    @Test
    @DisplayName("작성자 본인이면 true, 아니면 false를 반환한다")
    void isOwnedByChecksAuthor() {
        ReviewComment comment = comment(1L, 10L);

        assertThat(comment.isOwnedBy(10L)).isTrue();
        assertThat(comment.isOwnedBy(99L)).isFalse();
    }

    @Test
    @DisplayName("경로의 리뷰에 달린 댓글인지 확인한다")
    void belongsToChecksReview() {
        ReviewComment comment = comment(1L, 10L);

        assertThat(comment.belongsTo(1L)).isTrue();
        assertThat(comment.belongsTo(2L)).isFalse();
    }

    @Test
    @DisplayName("parentId가 있으면 답글, 없으면 최상위 댓글이다")
    void isReplyChecksParent() {
        ReviewComment topLevel = comment(1L, 10L);
        ReviewComment reply = ReviewComment.builder()
                .reviewId(1L).userId(10L).parentId(5L).body("답글").build();

        assertThat(topLevel.isReply()).isFalse();
        assertThat(reply.isReply()).isTrue();
    }
}
