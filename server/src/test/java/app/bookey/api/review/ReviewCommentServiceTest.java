package app.bookey.api.review;

import app.bookey.api.review.dto.ReviewDtos.ReviewCommentView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.review.ReviewComment;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewCommentServiceTest {

    private ReviewComment comment(long id, long reviewId, long userId) {
        ReviewComment comment = ReviewComment.builder()
                .reviewId(reviewId).userId(userId).body("덧붙임 " + id)
                .build();
        set(comment, "id", id);
        return comment;
    }

    private ReviewComment reply(long id, long reviewId, long userId, long parentId) {
        ReviewComment reply = ReviewComment.builder()
                .reviewId(reviewId).userId(userId).parentId(parentId).body("답글 " + id)
                .build();
        set(reply, "id", id);
        return reply;
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
    @DisplayName("작성자·본문·mine을 매핑한다 — 조회자가 작성자면 mine=true")
    void mapsAuthorBodyAndMine() {
        ReviewComment comment = comment(1L, 7L, 10L);
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<ReviewCommentView> views =
                ReviewCommentService.assembleViews(List.of(comment), 10L, authors, Map.of());

        ReviewCommentView view = views.get(0);
        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.reviewId()).isEqualTo(7L);
        assertThat(view.authorId()).isEqualTo(10L);
        assertThat(view.authorNickname()).isEqualTo("작가");
        assertThat(view.body()).isEqualTo("덧붙임 1");
        assertThat(view.mine()).isTrue();
        assertThat(view.createdAt()).isNotNull(); // 저장 전이라 createdAt 이 없어도 now 로 채운다
    }

    @Test
    @DisplayName("조회자가 작성자와 다르면 mine=false")
    void mineIsFalseForOtherViewer() {
        ReviewComment comment = comment(1L, 7L, 10L);

        List<ReviewCommentView> views = ReviewCommentService.assembleViews(
                List.of(comment), 20L, Map.of(10L, user(10L, "작가")), Map.of());

        assertThat(views.get(0).mine()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void withdrawnAuthorFallsBackToUnknown() {
        ReviewComment comment = comment(1L, 7L, 99L);

        List<ReviewCommentView> views =
                ReviewCommentService.assembleViews(List.of(comment), 1L, Map.of(), Map.of());

        assertThat(views.get(0).authorNickname()).isEqualTo("알 수 없음");
        assertThat(views.get(0).authorAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("입력 순서를 그대로 유지한다")
    void preservesInputOrder() {
        List<ReviewComment> comments = List.of(comment(5L, 7L, 10L), comment(3L, 7L, 10L), comment(9L, 7L, 10L));

        List<ReviewCommentView> views = ReviewCommentService.assembleViews(
                comments, 10L, Map.of(10L, user(10L, "작가")), Map.of());

        assertThat(views).extracting(ReviewCommentView::id).containsExactly(5L, 3L, 9L);
    }

    @Test
    @DisplayName("최상위 댓글은 parentId가 없고 답글 수를 배치 맵에서 채운다")
    void mapsParentIdAndReplyCount() {
        ReviewComment comment = comment(1L, 7L, 10L);

        List<ReviewCommentView> views = ReviewCommentService.assembleViews(
                List.of(comment), 10L, Map.of(10L, user(10L, "작가")), Map.of(1L, 3L));

        assertThat(views.get(0).parentId()).isNull();
        assertThat(views.get(0).replyCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("답글 행은 parentId를 싣고 답글 수는 항상 0이다")
    void replyRowCarriesParentIdAndZeroReplyCount() {
        ReviewComment reply = reply(2L, 7L, 10L, 1L);

        List<ReviewCommentView> views = ReviewCommentService.assembleViews(
                List.of(reply), 10L, Map.of(10L, user(10L, "작가")), Map.of());

        assertThat(views.get(0).parentId()).isEqualTo(1L);
        assertThat(views.get(0).replyCount()).isZero();
    }

    @Test
    @DisplayName("답글에는 답글을 달 수 없다 — 1단계까지만")
    void requireRepliableRejectsReply() {
        ReviewComment reply = reply(2L, 7L, 10L, 1L);

        assertThatThrownBy(() -> ReviewCommentService.requireRepliable(reply))
                .isInstanceOf(ApiException.class)
                .hasMessage("답글에는 답글을 달 수 없습니다.")
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_REPLY_DEPTH);
    }
}
