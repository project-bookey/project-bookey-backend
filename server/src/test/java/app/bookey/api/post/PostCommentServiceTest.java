package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.PostCommentView;
import app.bookey.domain.post.PostComment;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostCommentServiceTest {

    private PostComment comment(long id, long postId, Long parentId, long userId) {
        PostComment comment = PostComment.builder()
                .postId(postId).userId(userId).parentId(parentId).body("댓글 " + id)
                .build();
        set(comment, "id", id);
        return comment;
    }

    private User user(long id, String nickname) {
        User user = User.builder().handle("handle" + id).nickname(nickname).avatarUrl("avatar-" + id).build();
        set(user, "id", id);
        return user;
    }

    /** id 는 엔티티 자신에, createdAt 은 BaseTimeEntity 에 있어 상위 클래스까지 올라가며 찾는다. */
    private void set(Object target, String field, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ────────────────────────────── assembleThreads ──────────────────────────────

    @Test
    @DisplayName("작성자·본문·mine을 매핑한다 — 조회자가 작성자면 mine=true, 루트의 parentId는 null")
    void mapsAuthorBodyAndMine() {
        PostComment root = comment(1L, 7L, null, 10L);
        Instant createdAt = Instant.parse("2026-09-01T00:00:00Z");
        set(root, "createdAt", createdAt);

        List<PostCommentView> views = PostCommentService.assembleThreads(
                List.of(root), Map.of(), 10L, Map.of(10L, user(10L, "작가")));

        PostCommentView view = views.get(0);
        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.postId()).isEqualTo(7L);
        assertThat(view.parentId()).isNull();
        assertThat(view.authorId()).isEqualTo(10L);
        assertThat(view.authorNickname()).isEqualTo("작가");
        assertThat(view.authorAvatarUrl()).isEqualTo("avatar-10");
        assertThat(view.body()).isEqualTo("댓글 1");
        assertThat(view.mine()).isTrue();
        assertThat(view.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("조회자가 작성자와 다르면 mine=false — 답글도 각자 판정한다")
    void mineIsJudgedPerComment() {
        PostComment root = comment(1L, 7L, null, 10L);
        PostComment reply = comment(2L, 7L, 1L, 20L);

        List<PostCommentView> views = PostCommentService.assembleThreads(
                List.of(root), Map.of(1L, List.of(reply)), 20L,
                Map.of(10L, user(10L, "작가"), 20L, user(20L, "답글쓴이")));

        assertThat(views.get(0).mine()).isFalse();
        assertThat(views.get(0).replies().get(0).mine()).isTrue();
    }

    @Test
    @DisplayName("저장 전이라 createdAt 이 없으면 now 로 채운다")
    void fillsCreatedAtWhenMissing() {
        List<PostCommentView> views = PostCommentService.assembleThreads(
                List.of(comment(1L, 7L, null, 10L)), Map.of(), 10L, Map.of(10L, user(10L, "작가")));

        assertThat(views.get(0).createdAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴한 작성자는 '알 수 없음'으로 대체한다")
    void withdrawnAuthorFallsBackToUnknown() {
        PostComment root = comment(1L, 7L, null, 99L);

        List<PostCommentView> views = PostCommentService.assembleThreads(
                List.of(root), Map.of(), 1L, Map.of());

        assertThat(views.get(0).authorNickname()).isEqualTo("알 수 없음");
        assertThat(views.get(0).authorAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("루트 댓글은 입력 순서를 그대로 유지한다")
    void preservesRootOrder() {
        List<PostComment> roots = List.of(
                comment(5L, 7L, null, 10L), comment(3L, 7L, null, 10L), comment(9L, 7L, null, 10L));

        List<PostCommentView> views = PostCommentService.assembleThreads(
                roots, Map.of(), 10L, Map.of(10L, user(10L, "작가")));

        assertThat(views).extracting(PostCommentView::id).containsExactly(5L, 3L, 9L);
    }

    @Test
    @DisplayName("답글을 부모 아래에 넘겨받은 순서(오래된 순)대로 묶는다 — 답글 행은 parentId 가 채워지고 replies 는 빈 목록")
    void groupsRepliesUnderParent() {
        PostComment first = comment(1L, 7L, null, 10L);
        PostComment second = comment(2L, 7L, null, 10L);
        Map<Long, List<PostComment>> repliesByParent = Map.of(
                1L, List.of(comment(11L, 7L, 1L, 20L), comment(12L, 7L, 1L, 30L)),
                2L, List.of(comment(13L, 7L, 2L, 20L)));

        List<PostCommentView> views = PostCommentService.assembleThreads(
                List.of(first, second), repliesByParent, 10L,
                Map.of(10L, user(10L, "작가"), 20L, user(20L, "답글쓴이"), 30L, user(30L, "다른이")));

        assertThat(views).extracting(PostCommentView::id).containsExactly(1L, 2L);
        assertThat(views.get(0).replies()).extracting(PostCommentView::id).containsExactly(11L, 12L);
        assertThat(views.get(1).replies()).extracting(PostCommentView::id).containsExactly(13L);

        PostCommentView reply = views.get(0).replies().get(0);
        assertThat(reply.parentId()).isEqualTo(1L);
        assertThat(reply.postId()).isEqualTo(7L);
        assertThat(reply.authorNickname()).isEqualTo("답글쓴이");
        assertThat(reply.replies()).isEmpty();
    }

    @Test
    @DisplayName("답글이 없는 루트는 replies 가 빈 목록이다")
    void rootWithoutRepliesGetsEmptyList() {
        PostComment withReply = comment(1L, 7L, null, 10L);
        PostComment alone = comment(2L, 7L, null, 10L);

        List<PostCommentView> views = PostCommentService.assembleThreads(
                List.of(withReply, alone), Map.of(1L, List.of(comment(11L, 7L, 1L, 10L))), 10L,
                Map.of(10L, user(10L, "작가")));

        assertThat(views.get(0).replies()).hasSize(1);
        assertThat(views.get(1).replies()).isEmpty();
    }
}
