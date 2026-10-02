package app.bookey.domain.post;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostRulesTest {

    private static List<Long> ids(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ────────────────────────────── 공개 범위 ──────────────────────────────

    @ParameterizedTest
    @EnumSource(value = PostVisibility.class, names = {"PUBLIC", "LINK", "PRIVATE"})
    @DisplayName("모임 밖 글은 PUBLIC·LINK·PRIVATE 를 쓸 수 있다")
    void plainPostAllowsPlainVisibilities(PostVisibility visibility) {
        assertThatCode(() -> PostRules.requireVisibility(false, visibility)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("모임 밖 글은 CLUB 공개를 쓸 수 없다")
    void plainPostRejectsClubVisibility() {
        assertInvalid(() -> PostRules.requireVisibility(false, PostVisibility.CLUB));
    }

    @ParameterizedTest
    @EnumSource(value = PostVisibility.class, names = {"PUBLIC", "CLUB"})
    @DisplayName("모임 글은 PUBLIC(모임+광장)·CLUB(모임만) 을 쓸 수 있다")
    void clubPostAllowsPublicAndClub(PostVisibility visibility) {
        assertThatCode(() -> PostRules.requireVisibility(true, visibility)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = PostVisibility.class, names = {"LINK", "PRIVATE"})
    @DisplayName("모임 글은 LINK·PRIVATE 를 쓸 수 없다")
    void clubPostRejectsLinkAndPrivate(PostVisibility visibility) {
        assertInvalid(() -> PostRules.requireVisibility(true, visibility));
    }

    @Test
    @DisplayName("공개 범위 null 은 수정 때 '유지' 라 통과한다")
    void nullVisibilityIsKept() {
        assertThatCode(() -> PostRules.requireVisibility(true, null)).doesNotThrowAnyException();
        assertThatCode(() -> PostRules.requireVisibility(false, null)).doesNotThrowAnyException();
    }

    // ────────────────────────────── 본문·사진 ──────────────────────────────

    @Test
    @DisplayName("작성 — 본문이 있으면 통과, 사진 10장까지")
    void acceptsBodyAndTenImages() {
        assertThatCode(() -> PostRules.requireContent(true, "본문", ids(10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("작성 — 빈 본문은 거절한다")
    void rejectsBlankBody() {
        assertInvalid(() -> PostRules.requireContent(true, "  ", null));
        assertInvalid(() -> PostRules.requireContent(true, null, null));
    }

    @Test
    @DisplayName("사진 11장은 거절한다")
    void rejectsElevenImages() {
        assertInvalid(() -> PostRules.requireContent(true, "본문", ids(11)));
    }

    @Test
    @DisplayName("수정 — 본문 null 은 유지라 통과하지만 빈 본문으로 바꿀 수는 없다")
    void updateKeepsNullBodyButRejectsBlank() {
        assertThatCode(() -> PostRules.requireContent(false, null, null))
                .doesNotThrowAnyException();
        assertInvalid(() -> PostRules.requireContent(false, "", null));
    }
}
