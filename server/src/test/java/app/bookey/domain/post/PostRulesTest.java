package app.bookey.domain.post;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostRulesTest {

    private static Map<String, Object> noteWithPages(int count) {
        List<Object> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(Map.of("id", "p" + i, "elements", List.of()));
        }
        return Map.of("v", 1, "kind", "grid", "pages", pages);
    }

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

    // ────────────────────────────── TEXT ──────────────────────────────

    @Test
    @DisplayName("TEXT 작성 — 본문이 있고 문서가 없으면 통과, 사진 10장·밑줄 10개까지")
    void textAcceptsBodyWithoutDocument() {
        assertThatCode(() -> PostRules.requireContent(PostFormat.TEXT, true, "본문", null, ids(10), ids(10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TEXT 작성 — 빈 본문은 거절한다")
    void textRejectsBlankBody() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.TEXT, true, "  ", null, null, null));
        assertInvalid(() -> PostRules.requireContent(PostFormat.TEXT, true, null, null, null, null));
    }

    @Test
    @DisplayName("TEXT — 노트 문서를 붙이면 거절한다")
    void textRejectsDocument() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.TEXT, true, "본문", noteWithPages(1), null, null));
        assertInvalid(() -> PostRules.requireContent(PostFormat.TEXT, false, null, noteWithPages(1), null, null));
    }

    @Test
    @DisplayName("TEXT — 사진 11장은 거절한다")
    void textRejectsElevenImages() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.TEXT, true, "본문", null, ids(11), null));
    }

    @Test
    @DisplayName("TEXT 수정 — 본문 null 은 유지라 통과하지만 빈 본문으로 바꿀 수는 없다")
    void textUpdateKeepsNullBodyButRejectsBlank() {
        assertThatCode(() -> PostRules.requireContent(PostFormat.TEXT, false, null, null, null, null))
                .doesNotThrowAnyException();
        assertInvalid(() -> PostRules.requireContent(PostFormat.TEXT, false, "", null, null, null));
    }

    // ────────────────────────────── NOTE ──────────────────────────────

    @Test
    @DisplayName("NOTE 작성 — 문서가 있으면 빈 본문도 통과, 사진 30장까지")
    void noteAcceptsEmptyBody() {
        assertThatCode(() -> PostRules.requireContent(PostFormat.NOTE, true, "", noteWithPages(1), ids(30), ids(10)))
                .doesNotThrowAnyException();
        assertThatCode(() -> PostRules.requireContent(PostFormat.NOTE, true, "", noteWithPages(6), null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NOTE 작성 — 문서가 없으면 거절한다")
    void noteRequiresDocument() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, true, "", null, null, null));
    }

    @Test
    @DisplayName("NOTE 수정 — 문서 null 은 유지라 통과한다")
    void noteUpdateKeepsNullDocument() {
        assertThatCode(() -> PostRules.requireContent(PostFormat.NOTE, false, null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NOTE — 페이지 0장·7장은 거절한다")
    void noteRejectsZeroAndSevenPages() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, true, "", noteWithPages(0), null, null));
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, true, "", noteWithPages(7), null, null));
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, false, null, noteWithPages(7), null, null));
    }

    @Test
    @DisplayName("NOTE — pages 가 없거나 배열이 아니면 거절한다")
    void noteRejectsMissingPagesArray() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, true, "", Map.of("v", 1), null, null));
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, true, "", Map.of("pages", "p1"), null, null));
    }

    @Test
    @DisplayName("NOTE — 사진 31장은 거절한다")
    void noteRejectsThirtyOneImages() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, true, "", noteWithPages(1), ids(31), null));
    }

    @Test
    @DisplayName("밑줄 11개는 형식과 관계없이 거절한다")
    void rejectsElevenQuotes() {
        assertInvalid(() -> PostRules.requireContent(PostFormat.TEXT, true, "본문", null, null, ids(11)));
        assertInvalid(() -> PostRules.requireContent(PostFormat.NOTE, true, "", noteWithPages(1), null, ids(11)));
    }

    @Test
    @DisplayName("문서 크기는 1MB 까지 — 넘으면 거절한다")
    void documentSizeLimit() {
        assertThatCode(() -> PostRules.requireDocumentSize(PostRules.MAX_DOCUMENT_BYTES)).doesNotThrowAnyException();
        assertInvalid(() -> PostRules.requireDocumentSize(PostRules.MAX_DOCUMENT_BYTES + 1));
        assertThat(PostRules.MAX_DOCUMENT_BYTES).isEqualTo(1024 * 1024);
    }
}
