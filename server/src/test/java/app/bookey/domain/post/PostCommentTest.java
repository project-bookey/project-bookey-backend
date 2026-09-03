package app.bookey.domain.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostCommentTest {

    private PostComment comment(Long postId, Long userId, Long parentId) {
        return PostComment.builder()
                .postId(postId).userId(userId).parentId(parentId).body("덧붙임").build();
    }

    @Test
    @DisplayName("작성자 본인이면 true, 아니면 false를 반환한다")
    void isOwnedByChecksAuthor() {
        PostComment comment = comment(1L, 10L, null);

        assertThat(comment.isOwnedBy(10L)).isTrue();
        assertThat(comment.isOwnedBy(99L)).isFalse();
    }

    @Test
    @DisplayName("경로의 독후감에 달린 댓글인지 확인한다")
    void belongsToChecksPost() {
        PostComment comment = comment(1L, 10L, null);

        assertThat(comment.belongsTo(1L)).isTrue();
        assertThat(comment.belongsTo(2L)).isFalse();
    }

    @Test
    @DisplayName("부모가 있으면 답글이다")
    void isReplyChecksParent() {
        assertThat(comment(1L, 10L, null).isReply()).isFalse();
        assertThat(comment(1L, 10L, 3L).isReply()).isTrue();
    }

    @Test
    @DisplayName("루트 댓글에만 답글을 달 수 있다 — 답글의 답글은 막는다")
    void canReplyToRootOnly() {
        assertThat(PostComment.canReplyTo(comment(1L, 10L, null))).isTrue();
        assertThat(PostComment.canReplyTo(comment(1L, 10L, 3L))).isFalse();
    }
}
