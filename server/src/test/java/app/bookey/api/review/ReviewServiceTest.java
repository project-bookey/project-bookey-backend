package app.bookey.api.review;

import app.bookey.api.review.dto.ReviewDtos.ReviewView;
import app.bookey.domain.review.Review;
import app.bookey.domain.review.VerificationLevel;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {

    private Review review(long id, long userId) {
        Review review = Review.builder()
                .userId(userId).bookId(3L).readingRecordId(5L)
                .rating((short) 4).body("좋았다")
                .verificationLevel(VerificationLevel.VERIFIED_FULL)
                .build();
        set(review, "id", id);
        return review;
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
    @DisplayName("배치로 모은 댓글 수를 그대로 싣는다")
    void toViewMapsCommentCount() {
        ReviewView view = ReviewService.toView(review(1L, 10L), user(10L, "작가"), 7L, false);

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.bookId()).isEqualTo(3L);
        assertThat(view.authorId()).isEqualTo(10L);
        assertThat(view.authorNickname()).isEqualTo("작가");
        assertThat(view.authorHandle()).isEqualTo("handle10");
        assertThat(view.commentCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void toViewFallsBackToUnknownAuthor() {
        ReviewView view = ReviewService.toView(review(1L, 99L), null, 0L, false);

        assertThat(view.authorNickname()).isEqualTo("알 수 없음");
        assertThat(view.authorHandle()).isNull();
        assertThat(view.commentCount()).isZero();
    }

    @Test
    @DisplayName("태그가 없으면 빈 배열이 아니라 빈 목록으로 내려간다")
    void toViewKeepsEmptyTagsAsEmptyList() {
        ReviewView view = ReviewService.toView(review(1L, 10L), user(10L, "작가"), 0L, false);

        assertThat(view.tags()).isEmpty();
    }

    @Test
    @DisplayName("작성자가 그 책을 완독했으면 '완독' 표시를 싣는다")
    void toViewCarriesAuthorFinished() {
        assertThat(ReviewService.toView(review(1L, 10L), user(10L, "작가"), 0L, true).authorFinished()).isTrue();
        assertThat(ReviewService.toView(review(1L, 10L), user(10L, "작가"), 0L, false).authorFinished()).isFalse();
    }

    @Test
    @DisplayName("고칠 때 별점을 지울 수 있다")
    void clearRating() {
        Review review = review(1L, 10L);
        review.edit("고친 글", null, null, null);
        assertThat(review.getRating()).isEqualTo((short) 4);

        review.clearRating();

        assertThat(review.getRating()).isNull();
        assertThat(review.getBody()).isEqualTo("고친 글");
    }
}
