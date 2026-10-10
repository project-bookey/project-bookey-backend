package app.bookey.admin;

import app.bookey.admin.dto.AdminContentDtos.AdminContentRow;
import app.bookey.admin.dto.AdminContentDtos.ContentAction;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.admin.ModerationResolution;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.admin.ModerationStatus;
import app.bookey.domain.admin.ModerationTicket;
import app.bookey.domain.admin.ModerationTicketRepository;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.ClubPostRepository;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostComment;
import app.bookey.domain.post.PostCommentRepository;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.remark.BookRemarkRepository;
import app.bookey.domain.report.AbuseReportRepository;
import app.bookey.domain.review.Review;
import app.bookey.domain.review.ReviewComment;
import app.bookey.domain.review.ReviewCommentRepository;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminContentServiceTest {

    private static final AuthAdmin OPERATOR = new AuthAdmin(1L, "op@bookey.app", AdminRole.OPERATOR);
    private static final AuthAdmin SUPPORT = new AuthAdmin(2L, "cs@bookey.app", AdminRole.SUPPORT);
    private static final AuthAdmin VIEWER = new AuthAdmin(3L, "view@bookey.app", AdminRole.VIEWER);

    private final PostRepository postRepository = mock(PostRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final PostCommentRepository postCommentRepository = mock(PostCommentRepository.class);
    private final ReviewCommentRepository reviewCommentRepository = mock(ReviewCommentRepository.class);
    private final AbuseReportRepository reportRepository = mock(AbuseReportRepository.class);
    private final ModerationTicketRepository ticketRepository = mock(ModerationTicketRepository.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final AdminContentService service = new AdminContentService(postRepository, reviewRepository,
            mock(ClubPostRepository.class), postCommentRepository, reviewCommentRepository,
            mock(BookRemarkRepository.class), mock(UserRepository.class), mock(BookRepository.class),
            mock(ClubRepository.class), reportRepository, ticketRepository, auditService);

    private static <T> T withId(T entity, long id) {
        try {
            Class<?> type = entity.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return entity;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new IllegalStateException("id");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Post post(long id, PostVisibility visibility) {
        Post post = withId(Post.builder().userId(10L).bookId(5L).slug("s").title("제목").bodyMd("본문 내용")
                .visibility(visibility).build(), id);
        when(postRepository.findById(id)).thenReturn(Optional.of(post));
        return post;
    }

    @Test
    @DisplayName("독후감 숨김 — 상태를 바꾸고, 열린 신고 티켓을 HIDE 로 닫고, 신고도 처리한다")
    void hidePostResolvesTicket() {
        Post post = post(30L, PostVisibility.PUBLIC);
        ModerationTicket ticket = new ModerationTicket(ModerationSource.POST, 30L, "스팸");
        when(ticketRepository.findBySourceTypeAndSourceId(ModerationSource.POST, 30L)).thenReturn(Optional.of(ticket));

        service.act(OPERATOR, ModerationSource.POST, 30L, ContentAction.HIDE, "광고 글");

        assertThat(post.isVisible()).isFalse();
        assertThat(post.isReadableBy(10L, false)).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(ModerationStatus.RESOLVED);
        assertThat(ticket.getResolution()).isEqualTo(ModerationResolution.HIDE);
        verify(reportRepository).resolveAllForTarget("POST", 30L);
        verify(auditService).log(eq(OPERATOR), eq("HIDE_CONTENT"), eq("POST"), eq(30L), eq("광고 글"), anyMap(),
                eq(Map.of("status", "HIDDEN")));
    }

    @Test
    @DisplayName("댓글은 완전 삭제만 된다 — 지운 원문을 감사 로그에 남긴다")
    void deleteCommentKeepsTextInAudit() {
        PostComment comment = withId(PostComment.builder().postId(30L).userId(11L).body("욕설 댓글 원문").build(), 70L);
        when(postCommentRepository.findById(70L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.act(OPERATOR, ModerationSource.POST_COMMENT, 70L, ContentAction.HIDE, "x"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        service.act(OPERATOR, ModerationSource.POST_COMMENT, 70L, ContentAction.DELETE, "욕설");

        verify(postCommentRepository).delete(comment);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(eq(OPERATOR), eq("DELETE_CONTENT"), eq("POST_COMMENT"), eq(70L), eq("욕설"),
                before.capture(), any());
        assertThat(before.getValue()).containsEntry("text", "욕설 댓글 원문");
    }

    @Test
    @DisplayName("CS 담당·보기 전용은 콘텐츠를 조치할 수 없다")
    void onlyModeratorsAct() {
        post(30L, PostVisibility.PUBLIC);
        assertThatThrownBy(() -> service.act(SUPPORT, ModerationSource.POST, 30L, ContentAction.HIDE, "사유"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        assertThatThrownBy(() -> service.act(VIEWER, ModerationSource.POST, 30L, ContentAction.HIDE, "사유"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("비공개 독후감 원문은 신고 처리 권한이 있어야 보고, 목록에서도 내용을 가린다")
    void privatePostsAreMasked() {
        post(31L, PostVisibility.PRIVATE);
        assertThatThrownBy(() -> service.detail(SUPPORT, ModerationSource.POST, 31L))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        assertThat(service.detail(OPERATOR, ModerationSource.POST, 31L).body()).contains("본문 내용");

        Post hidden = postRepository.findById(31L).orElseThrow();
        @SuppressWarnings("unchecked")
        Specification<Post> anySpec = any(Specification.class);
        when(postRepository.findAll(anySpec, any(Pageable.class))).thenReturn(new PageImpl<>(List.of(hidden)));
        AdminContentRow row = service.list(VIEWER, ModerationSource.POST, null, 0, 20).content().getFirst();
        assertThat(row.preview()).isEqualTo("(비공개 글)");
        assertThat(row.supportedActions()).isEmpty();
    }

    @Test
    @DisplayName("신고 판정 반영 — 리뷰 '유지' 는 복구, 리뷰 댓글 '제재' 는 숨김이 없어 삭제하고 작성자를 돌려준다")
    void applyResolution() {
        Review review = withId(Review.builder().userId(12L).bookId(5L).body("리뷰").build(), 40L);
        review.hide();
        when(reviewRepository.findById(40L)).thenReturn(Optional.of(review));
        assertThat(service.applyResolution(ModerationSource.REVIEW, 40L, ModerationResolution.KEEP)).isEqualTo(12L);
        assertThat(review.isVisible()).isTrue();

        ReviewComment comment = withId(ReviewComment.builder().reviewId(40L).userId(13L).body("비방").build(), 80L);
        when(reviewCommentRepository.findById(80L)).thenReturn(Optional.of(comment));
        assertThat(service.applyResolution(ModerationSource.REVIEW_COMMENT, 80L, ModerationResolution.SANCTION))
                .isEqualTo(13L);
        verify(reviewCommentRepository).delete(comment);
        verify(postCommentRepository, never()).delete(any(PostComment.class));
    }
}
