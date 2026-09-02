package app.bookey.api.quote;

import app.bookey.api.quote.dto.QuoteDtos.QuoteCommentView;
import app.bookey.domain.quote.QuoteComment;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteCommentServiceTest {

    private QuoteComment comment(long id, long quoteId, long userId) {
        QuoteComment comment = QuoteComment.builder()
                .quoteId(quoteId).userId(userId).body("덧붙임 " + id)
                .build();
        set(comment, "id", id);
        return comment;
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
        QuoteComment comment = comment(1L, 7L, 10L);
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<QuoteCommentView> views = QuoteCommentService.assembleViews(List.of(comment), 10L, authors);

        QuoteCommentView view = views.get(0);
        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.quoteId()).isEqualTo(7L);
        assertThat(view.authorId()).isEqualTo(10L);
        assertThat(view.authorNickname()).isEqualTo("작가");
        assertThat(view.body()).isEqualTo("덧붙임 1");
        assertThat(view.mine()).isTrue();
        assertThat(view.createdAt()).isNotNull(); // 저장 전이라 createdAt 이 없어도 now 로 채운다
    }

    @Test
    @DisplayName("조회자가 작성자와 다르면 mine=false")
    void mineIsFalseForOtherViewer() {
        QuoteComment comment = comment(1L, 7L, 10L);

        List<QuoteCommentView> views = QuoteCommentService.assembleViews(
                List.of(comment), 20L, Map.of(10L, user(10L, "작가")));

        assertThat(views.get(0).mine()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void withdrawnAuthorFallsBackToUnknown() {
        QuoteComment comment = comment(1L, 7L, 99L);

        List<QuoteCommentView> views = QuoteCommentService.assembleViews(List.of(comment), 1L, Map.of());

        assertThat(views.get(0).authorNickname()).isEqualTo("알 수 없음");
        assertThat(views.get(0).authorAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("입력 순서를 그대로 유지한다")
    void preservesInputOrder() {
        List<QuoteComment> comments = List.of(comment(5L, 7L, 10L), comment(3L, 7L, 10L), comment(9L, 7L, 10L));

        List<QuoteCommentView> views = QuoteCommentService.assembleViews(
                comments, 10L, Map.of(10L, user(10L, "작가")));

        assertThat(views).extracting(QuoteCommentView::id).containsExactly(5L, 3L, 9L);
    }
}
