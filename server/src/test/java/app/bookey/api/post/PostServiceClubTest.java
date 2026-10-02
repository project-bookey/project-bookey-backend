package app.bookey.api.post;

import app.bookey.api.club.ClubService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.post.dto.PostDtos.CreatePostRequest;
import app.bookey.api.post.dto.PostDtos.UpdatePostRequest;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.Club;
import app.bookey.domain.club.ClubMember;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.club.ClubStatus;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostCommentRepository;
import app.bookey.domain.post.PostFormat;
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
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 모임 독후감 규칙 — 작성 자격(활성 멤버·진행 중 모임), CLUB 공개 글 읽기, 모임별 목록 멤버 제한. */
class PostServiceClubTest {

    private static final long CLUB_ID = 3L;
    private static final long AUTHOR = 10L;
    private static final long MEMBER = 20L;
    private static final long OUTSIDER = 30L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final ClubService clubService = mock(ClubService.class);
    private final ClubMemberRepository memberRepository = mock(ClubMemberRepository.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService(postRepository, mock(PostImageRepository.class),
                mock(PostLikeRepository.class), mock(PostCommentRepository.class),
                mock(BookRepository.class), mock(ReadingRecordRepository.class), mock(UserRepository.class),
                clubService, mock(ClubRepository.class), memberRepository, new ObjectMapper(),
                mock(NotificationService.class), rateLimiter);
    }

    private static Club club(ClubStatus status) {
        Club club = mock(Club.class);
        when(club.getStatus()).thenReturn(status);
        return club;
    }

    private static ClubMember activeMember() {
        ClubMember member = mock(ClubMember.class);
        when(member.isActive()).thenReturn(true);
        return member;
    }

    private static CreatePostRequest request(PostVisibility visibility, PostFormat format,
                                             Map<String, Object> document, Long clubId) {
        return new CreatePostRequest(null, null, "제목", format == PostFormat.NOTE ? "" : "본문", visibility,
                null, null, null, format, document, clubId);
    }

    private static Post clubPost(PostVisibility visibility) {
        Post post = Post.builder()
                .userId(AUTHOR).slug("s").title("제목").bodyMd("본문")
                .visibility(visibility).clubId(CLUB_ID)
                .build();
        try {
            Field id = Post.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(post, 1L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return post;
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(code);
    }

    // ────────────────────────────── 작성 ──────────────────────────────

    @Test
    @DisplayName("모임 멤버가 아니면 모임 독후감을 쓸 수 없다 — CLUB_NOT_MEMBER")
    void createRejectsNonMember() {
        Club club = club(ClubStatus.ACTIVE);
        when(clubService.getClub(CLUB_ID)).thenReturn(club);
        when(clubService.activeMember(CLUB_ID, OUTSIDER)).thenThrow(ApiException.of(ErrorCode.CLUB_NOT_MEMBER));

        assertCode(() -> service.create(OUTSIDER, request(PostVisibility.CLUB, null, null, CLUB_ID)),
                ErrorCode.CLUB_NOT_MEMBER);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("끝난 모임에는 독후감을 쓸 수 없다 — CLUB_ENDED")
    void createRejectsEndedClub() {
        Club club = club(ClubStatus.ENDED);
        when(clubService.getClub(CLUB_ID)).thenReturn(club);
        ClubMember member = activeMember();
        when(clubService.activeMember(CLUB_ID, MEMBER)).thenReturn(member);

        assertCode(() -> service.create(MEMBER, request(PostVisibility.CLUB, null, null, CLUB_ID)),
                ErrorCode.CLUB_ENDED);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("모임 글에 PRIVATE, 모임 밖 글에 CLUB 을 쓰면 멤버십을 보기 전에 400")
    void createRejectsVisibilityMismatch() {
        assertCode(() -> service.create(MEMBER, request(PostVisibility.PRIVATE, null, null, CLUB_ID)),
                ErrorCode.INVALID_REQUEST);
        assertCode(() -> service.create(MEMBER, request(PostVisibility.CLUB, null, null, null)),
                ErrorCode.INVALID_REQUEST);
        verify(clubService, never()).getClub(any());
    }

    @Test
    @DisplayName("노트 문서의 페이지가 7장이면 400")
    void createRejectsSevenPageNote() {
        List<Object> pages = List.of(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        assertCode(() -> service.create(MEMBER, request(PostVisibility.PUBLIC, PostFormat.NOTE,
                Map.of("pages", pages), null)), ErrorCode.INVALID_REQUEST);
    }

    // ────────────────────────────── 수정 ──────────────────────────────

    @Test
    @DisplayName("모임 글을 LINK 로 바꿀 수 없고, TEXT 글에 노트 문서를 보낼 수 없다")
    void updateEnforcesClubVisibilityAndFormat() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(clubPost(PostVisibility.CLUB)));

        assertCode(() -> service.update(AUTHOR, 1L, new UpdatePostRequest(null, null, null, null,
                PostVisibility.LINK, null, null, null)), ErrorCode.INVALID_REQUEST);
        assertCode(() -> service.update(AUTHOR, 1L, new UpdatePostRequest(null, null, null, null,
                null, null, null, Map.of("pages", List.of(Map.of())))), ErrorCode.INVALID_REQUEST);
    }

    // ────────────────────────────── 읽기 ──────────────────────────────

    @Test
    @DisplayName("CLUB 공개 글 — 활성 멤버는 읽고, 멤버가 아니면 없는 글로 본다")
    void clubPostReadableByActiveMembersOnly() {
        Post post = clubPost(PostVisibility.CLUB);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        ClubMember member = activeMember();
        when(memberRepository.findByClubIdAndUserId(CLUB_ID, MEMBER)).thenReturn(Optional.of(member));
        when(memberRepository.findByClubIdAndUserId(CLUB_ID, OUTSIDER)).thenReturn(Optional.empty());

        assertThat(service.readable(MEMBER, 1L)).isSameAs(post);
        assertThat(service.readable(AUTHOR, 1L)).isSameAs(post);
        assertCode(() -> service.readable(OUTSIDER, 1L), ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("CLUB 공개 글 — 모임을 나간(비활성) 멤버는 읽을 수 없다")
    void clubPostHiddenFromInactiveMembers() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(clubPost(PostVisibility.CLUB)));
        ClubMember left = mock(ClubMember.class);
        when(left.isActive()).thenReturn(false);
        when(memberRepository.findByClubIdAndUserId(CLUB_ID, MEMBER)).thenReturn(Optional.of(left));

        assertCode(() -> service.readable(MEMBER, 1L), ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("모임+광장(PUBLIC) 모임 글은 멤버가 아니어도 읽는다 — 멤버십을 조회하지 않는다")
    void publicClubPostReadableByAnyone() {
        Post post = clubPost(PostVisibility.PUBLIC);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThat(service.readable(OUTSIDER, 1L)).isSameAs(post);
        verify(memberRepository, never()).findByClubIdAndUserId(any(), any());
    }

    // ────────────────────────────── 모임별 목록 ──────────────────────────────

    @Test
    @DisplayName("모임별 목록은 활성 멤버만 — 아니면 CLUB_NOT_MEMBER 이고 글을 읽지 않는다")
    void listByClubRequiresActiveMember() {
        Club club = club(ClubStatus.ACTIVE);
        when(clubService.getClub(CLUB_ID)).thenReturn(club);
        when(clubService.activeMember(CLUB_ID, OUTSIDER)).thenThrow(ApiException.of(ErrorCode.CLUB_NOT_MEMBER));

        assertCode(() -> service.listByClub(OUTSIDER, CLUB_ID, PageRequest.of(0, 20)), ErrorCode.CLUB_NOT_MEMBER);
        verify(postRepository, never()).findAllByClubIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    @DisplayName("없는 모임의 목록은 CLUB_NOT_FOUND")
    void listByClubRejectsMissingClub() {
        when(clubService.getClub(CLUB_ID)).thenThrow(ApiException.of(ErrorCode.CLUB_NOT_FOUND));

        assertCode(() -> service.listByClub(MEMBER, CLUB_ID, PageRequest.of(0, 20)), ErrorCode.CLUB_NOT_FOUND);
    }
}
