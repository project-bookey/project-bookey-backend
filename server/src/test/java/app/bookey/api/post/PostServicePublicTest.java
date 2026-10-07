package app.bookey.api.post;

import app.bookey.api.club.ClubService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostCommentRepository;
import app.bookey.domain.post.PostImageRepository;
import app.bookey.domain.post.PostLikeRepository;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 비회원 독후감 조회(공개 웹) — 공개 범위, 열람자 값, IP 키 조회수와 봇 제외, 없는 책의 공개 목록. */
class PostServicePublicTest {

    private static final long AUTHOR = 10L;
    private static final long POST_ID = 1L;
    private static final String VIEWER = "ip:0123456789abcdef";

    private final PostRepository postRepository = mock(PostRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ClubMemberRepository memberRepository = mock(ClubMemberRepository.class);
    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService(postRepository, mock(PostImageRepository.class),
                mock(PostLikeRepository.class), mock(PostCommentRepository.class),
                bookRepository, mock(ReadingRecordRepository.class), userRepository,
                mock(ClubService.class), mock(ClubRepository.class), memberRepository,
                mock(NotificationService.class), rateLimiter);
    }

    private Post post(PostVisibility visibility) {
        Post post = Post.builder()
                .userId(AUTHOR).slug("s").title("제목").bodyMd("본문")
                .visibility(visibility)
                .build();
        try {
            Field id = Post.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(post, POST_ID);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        return post;
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("비회원에게 비공개 글은 없는 글이다 — POST_NOT_FOUND")
    void privatePostIsNotFound() {
        post(PostVisibility.PRIVATE);
        assertCode(() -> service.getPublic(POST_ID, VIEWER, false), ErrorCode.POST_NOT_FOUND);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("비회원에게 모임 공개 글은 없는 글이다 — 멤버십을 따질 사람이 없다")
    void clubPostIsNotFound() {
        Post post = post(PostVisibility.CLUB);
        set(post, "clubId", 3L);
        assertCode(() -> service.getPublic(POST_ID, VIEWER, false), ErrorCode.POST_NOT_FOUND);
        verify(memberRepository, never()).findByClubIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("공개 글 — 열람자 값(mine·likedByMe)은 false 이고, 조회수는 IP 키·글당 1시간에 한 번만 센다")
    void publicPostCountsViewOncePerViewerKey() {
        Post post = post(PostVisibility.PUBLIC);
        when(rateLimiter.tryAcquire("post:view:1:" + VIEWER, 1, Duration.ofHours(1))).thenReturn(true, false);

        PostView first = service.getPublic(POST_ID, VIEWER, false);
        assertThat(first.mine()).isFalse();
        assertThat(first.likedByMe()).isFalse();
        assertThat(post.getViewCount()).isEqualTo(1);

        service.getPublic(POST_ID, VIEWER, false);
        assertThat(post.getViewCount()).isEqualTo(1);
        verify(rateLimiter, org.mockito.Mockito.times(2))
                .tryAcquire("post:view:1:" + VIEWER, 1, Duration.ofHours(1));
    }

    @Test
    @DisplayName("링크 공개 글도 비회원이 읽을 수 있다")
    void linkPostIsReadable() {
        post(PostVisibility.LINK);
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        assertThat(service.getPublic(POST_ID, VIEWER, false).visibility()).isEqualTo(PostVisibility.LINK);
    }

    @Test
    @DisplayName("봇의 열람은 조회수로 세지 않는다 — 리미터도 묻지 않는다")
    void botViewIsNotCounted() {
        Post post = post(PostVisibility.PUBLIC);
        service.getPublic(POST_ID, VIEWER, true);
        assertThat(post.getViewCount()).isZero();
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("탈퇴한 사람의 공개 글은 비회원에게도 없는 글이다")
    void terminatedAuthorPostIsNotFound() {
        post(PostVisibility.PUBLIC);
        when(userRepository.isTerminated(AUTHOR)).thenReturn(true);
        assertCode(() -> service.getPublic(POST_ID, VIEWER, false), ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 책의 공개 독후감 목록은 빈 목록이 아니라 BOOK_NOT_FOUND")
    void publicBookPostsRequireBook() {
        when(bookRepository.existsById(99L)).thenReturn(false);
        assertCode(() -> service.listPublicByBook(99L, PageRequest.of(0, 10)), ErrorCode.BOOK_NOT_FOUND);
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
