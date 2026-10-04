package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.ClubHomeView;
import app.bookey.api.club.dto.ClubDtos.ClubSummaryView;
import app.bookey.api.club.dto.ClubDtos.CreateClubRequest;
import app.bookey.api.club.dto.ClubDtos.JoinPublicRequest;
import app.bookey.api.club.dto.ClubDtos.JoinRequest;
import app.bookey.api.club.dto.ClubDtos.KickRequest;
import app.bookey.api.library.ProgressService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.admin.OpsFlagRepository;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.book.BookSource;
import app.bookey.domain.club.*;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 모임 생성 정원 제한, 정원을 건드리는 참가 경로의 행 잠금, 읽을 책이 아직 없는 모임의 홈, 목록 카드의 내 다음 모임,
 *  나가기·내보내기 때 지우는 흔적, 탈퇴할 때 클럽 나가기와 호스트 넘기기. */
class ClubServiceTest {

    private static final BookeyProperties.Club CLUB_POLICY =
            new BookeyProperties.Club(50, 10, 10, 2, Duration.ofHours(24), 3, 10);

    private final ClubRepository clubRepository = mock(ClubRepository.class);
    private final ClubMemberRepository memberRepository = mock(ClubMemberRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final ClubMeetingRepository meetingRepository = mock(ClubMeetingRepository.class);
    private final ClubChatUnlockRepository chatUnlockRepository = mock(ClubChatUnlockRepository.class);
    private final ClubChatReadRepository chatReadRepository = mock(ClubChatReadRepository.class);
    private final ClubMeetingAttendeeRepository attendeeRepository = mock(ClubMeetingAttendeeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ClubService service = new ClubService(
            clubRepository,
            mock(ClubBookRepository.class),
            memberRepository,
            meetingRepository,
            mock(ClubPostRepository.class),
            mock(ClubEventRepository.class),
            chatUnlockRepository,
            chatReadRepository,
            attendeeRepository,
            bookRepository,
            mock(ReadingRecordRepository.class),
            mock(ReadingSessionRepository.class),
            userRepository,
            mock(OpsFlagRepository.class),
            mock(ProgressService.class),
            mock(RateLimiter.class),
            new BookeyProperties(null, null, null, null, CLUB_POLICY, null, null, null, null, null));

    private static CreateClubRequest create(Integer memberLimit) {
        return new CreateClubRequest("월요일의 데미안", null, 7L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                ClubVisibility.CODE_ONLY, memberLimit, true, false, null);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @Test
    @DisplayName("무료 정원(10명)을 넘겨 모임을 만들 수 없다 — 책 조회 전에 거절한다")
    void createRejectsOverFreeLimit() {
        assertThatThrownBy(() -> service.create(1L, create(11)))
                .isInstanceOf(ApiException.class)
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(bookRepository, never()).findById(any());
    }

    @Test
    @DisplayName("정원을 비우면 무료 정원으로 통과해 다음 단계로 간다")
    void createDefaultsWithinFreeLimit() {
        when(bookRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, create(null)))
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    @DisplayName("옛 앱이 작은 정원(3명)을 보내도 무료 정원(10명)으로 연다")
    void createAlwaysOpensWithFreeLimit() {
        when(bookRepository.findById(7L))
                .thenReturn(Optional.of(Book.builder().title("데미안").source(BookSource.MANUAL).build()));
        ArgumentCaptor<Club> saved = ArgumentCaptor.forClass(Club.class);
        // 정원만 보면 되므로 저장 다음 단계는 예외로 끊는다.
        when(clubRepository.save(saved.capture())).thenThrow(new IllegalStateException("stop"));

        assertThatThrownBy(() -> service.create(1L, create(3))).isInstanceOf(IllegalStateException.class);
        assertThat(saved.getValue().getMemberLimit()).isEqualTo((short) 10);
    }

    @Test
    @DisplayName("코드 참가는 모임 행을 잠가 읽는다")
    void joinByCodeLocksClub() {
        when(clubRepository.findByJoinCodeForUpdate("ABC234")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(2L, new JoinRequest("abc234", true, true)))
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_CODE_INVALID);
        verify(clubRepository).findByJoinCodeForUpdate("ABC234");
        verify(clubRepository, never()).findByJoinCode(any());
    }

    @Test
    @DisplayName("공개 모임 참가도 모임 행을 잠가 읽는다")
    void joinPublicLocksClub() {
        when(clubRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinPublic(2L, 10L, new JoinPublicRequest(true, true)))
                .extracting(ClubServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOT_FOUND);
        verify(clubRepository).findByIdForUpdate(10L);
        verify(clubRepository, never()).findById(any());
    }

    @Test
    @DisplayName("읽을 책을 고른 모임이 아직 없으면 홈은 책 없이, 기록 없는 멤버도 진척 null 로 그린다")
    void homeWithoutCurrentBook() {
        Club club = Club.builder().ownerId(1L).name("월요일의 데미안").joinCode("ABC234")
                .memberLimit((short) 3).startsAt(LocalDate.of(2026, 10, 3)).allowNudge(true).build();
        ClubMember host = ClubMember.builder().clubId(10L).userId(1L)
                .role(ClubRole.HOST).shareProgress(true).allowNudge(true).build();
        when(clubRepository.findById(10L)).thenReturn(Optional.of(club));
        when(memberRepository.findByClubIdAndUserId(10L, 1L)).thenReturn(Optional.of(host));
        when(memberRepository.findAllByClubIdAndStatus(10L, ClubMemberStatus.ACTIVE)).thenReturn(List.of(host));

        ClubHomeView home = service.home(1L, 10L);

        assertThat(home.book()).isNull();
        assertThat(home.endsAt()).isNull();
        assertThat(home.checkpoints()).isEmpty();
        assertThat(home.members()).singleElement()
                .satisfies(m -> assertThat(m.completionRate()).isNull());
    }

    @Test
    @DisplayName("목록 카드의 내 다음 모임은 클럽마다 내가 참여한 가장 가까운 모임 — 없으면 null")
    void myClubsCarriesMyNearestAttendingMeeting() throws ReflectiveOperationException {
        Club withMeetings = club(10L);
        Club withoutMeetings = club(20L);
        ClubMember first = ClubMember.builder().clubId(10L).userId(1L).role(ClubRole.MEMBER).build();
        ClubMember second = ClubMember.builder().clubId(20L).userId(1L).role(ClubRole.MEMBER).build();
        Pageable page = PageRequest.of(0, 20);
        when(memberRepository.findMyClubs(1L, page)).thenReturn(new PageImpl<>(List.of(first, second), page, 2));
        when(clubRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(withMeetings, withoutMeetings));
        Instant near = Instant.parse("2026-10-12T10:00:00Z");
        // 저장소가 이른 순으로 준다 — 같은 클럽의 더 늦은 모임은 버린다.
        when(meetingRepository.findAttendingUpcoming(eq(1L), eq(List.of(10L, 20L)), any())).thenReturn(List.of(
                new ClubMeeting(10L, 2L, "10월 모임", null, near, null, "북카페", "서울", null, null, null, null, null, null),
                new ClubMeeting(10L, 2L, "11월 모임", null, near.plus(Duration.ofDays(30)), null, "북카페", "서울",
                        null, null, null, null, null, null)));

        PageResponse<ClubSummaryView> clubs = service.myClubs(1L, page);

        assertThat(clubs.content()).hasSize(2);
        assertThat(clubs.content().get(0).myNextMeetingAt()).isEqualTo(near);
        assertThat(clubs.content().get(0).myNextMeetingTitle()).isEqualTo("10월 모임");
        assertThat(clubs.content().get(1).myNextMeetingAt()).isNull();
        assertThat(clubs.content().get(1).myNextMeetingTitle()).isNull();
    }

    @Test
    @DisplayName("나가면 채팅 이용권·읽음 위치·아직 시작하지 않은 모임 참여를 지운다 — 다시 참가하면 새 멤버로 시작한다")
    void leaveForgetsChatAccessAndUpcomingAttendance() {
        ClubMember member = givenTwoMemberClub();

        service.leave(2L, 10L);

        assertThat(member.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        verify(chatUnlockRepository).deleteByClubIdAndUserId(10L, 2L);
        verify(chatReadRepository).deleteByClubIdAndUserId(10L, 2L);
        verify(attendeeRepository).deleteUpcomingByClubIdAndUserId(eq(10L), eq(2L), any(Instant.class));
    }

    @Test
    @DisplayName("내보낸 멤버도 채팅 이용권·읽음 위치·아직 시작하지 않은 모임 참여를 지운다")
    void kickForgetsChatAccessAndUpcomingAttendance() {
        ClubMember member = givenTwoMemberClub();

        service.kick(1L, 10L, new KickRequest(2L, "광고"));

        assertThat(member.getStatus()).isEqualTo(ClubMemberStatus.KICKED);
        verify(chatUnlockRepository).deleteByClubIdAndUserId(10L, 2L);
        verify(chatReadRepository).deleteByClubIdAndUserId(10L, 2L);
        verify(attendeeRepository).deleteUpcomingByClubIdAndUserId(eq(10L), eq(2L), any(Instant.class));
    }

    @Test
    @DisplayName("탈퇴하면 클럽을 나가고, 호스트 자리는 먼저 들어온 멤버보다 운영진에게 먼저 넘긴다")
    void withdrawalHandsHostToModeratorFirst() throws ReflectiveOperationException {
        Club club = givenClubOwnedBy(1L, 3);
        ClubMember host = member(1L, ClubRole.HOST, "2026-09-01T00:00:00Z");
        ClubMember early = member(2L, ClubRole.MEMBER, "2026-09-02T00:00:00Z");
        ClubMember moderator = member(3L, ClubRole.MODERATOR, "2026-09-05T00:00:00Z");
        when(memberRepository.findAllByUserIdAndStatus(1L, ClubMemberStatus.ACTIVE)).thenReturn(List.of(host));
        when(memberRepository.findAllByClubIdAndStatus(10L, ClubMemberStatus.ACTIVE))
                .thenReturn(List.of(host, early, moderator));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(2L), user(3L)));

        service.leaveAllOnWithdrawal(1L);

        assertThat(club.getOwnerId()).isEqualTo(3L);
        assertThat(moderator.getRole()).isEqualTo(ClubRole.HOST);
        assertThat(host.getRole()).isEqualTo(ClubRole.MEMBER);
        assertThat(host.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        assertThat(club.getMemberCount()).isEqualTo((short) 2);
        assertThat(club.getStatus().isOver()).isFalse();
        verify(chatUnlockRepository).deleteByClubIdAndUserId(10L, 1L);
        verify(attendeeRepository).deleteUpcomingByClubIdAndUserId(eq(10L), eq(1L), any(Instant.class));
    }

    @Test
    @DisplayName("탈퇴한 멤버는 호스트 자리를 이어받지 않는다 — 다음으로 먼저 들어온 멤버가 받는다")
    void withdrawalSkipsTerminatedSuccessor() throws ReflectiveOperationException {
        Club club = givenClubOwnedBy(1L, 3);
        ClubMember host = member(1L, ClubRole.HOST, "2026-09-01T00:00:00Z");
        ClubMember gone = member(2L, ClubRole.MEMBER, "2026-09-02T00:00:00Z");
        ClubMember next = member(3L, ClubRole.MEMBER, "2026-09-03T00:00:00Z");
        when(memberRepository.findAllByUserIdAndStatus(1L, ClubMemberStatus.ACTIVE)).thenReturn(List.of(host));
        when(memberRepository.findAllByClubIdAndStatus(10L, ClubMemberStatus.ACTIVE))
                .thenReturn(List.of(host, gone, next));
        User terminated = user(2L);
        terminated.changeStatus(UserStatus.TERMINATED);
        when(userRepository.findAllById(any())).thenReturn(List.of(terminated, user(3L)));

        service.leaveAllOnWithdrawal(1L);

        assertThat(club.getOwnerId()).isEqualTo(3L);
        assertThat(next.getRole()).isEqualTo(ClubRole.HOST);
        assertThat(gone.getRole()).isEqualTo(ClubRole.MEMBER);
    }

    @Test
    @DisplayName("혼자 남은 호스트가 탈퇴하면 클럽을 끝낸다")
    void withdrawalEndsClubWithNoOneLeft() throws ReflectiveOperationException {
        Club club = givenClubOwnedBy(1L, 1);
        ClubMember host = member(1L, ClubRole.HOST, "2026-09-01T00:00:00Z");
        when(memberRepository.findAllByUserIdAndStatus(1L, ClubMemberStatus.ACTIVE)).thenReturn(List.of(host));
        when(memberRepository.findAllByClubIdAndStatus(10L, ClubMemberStatus.ACTIVE)).thenReturn(List.of(host));

        service.leaveAllOnWithdrawal(1L);

        assertThat(host.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        assertThat(club.getMemberCount()).isZero();
        assertThat(club.getStatus()).isEqualTo(ClubStatus.ENDED);
    }

    /** 사람 수만큼 참가한 모임 10 — 탈퇴 경로는 행을 잠가 읽는다. */
    private Club givenClubOwnedBy(Long ownerId, int people) throws ReflectiveOperationException {
        Club club = club(10L);
        club.transferHost(ownerId);
        for (int i = 0; i < people; i++) {
            club.joinMember();
        }
        when(clubRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
        return club;
    }

    private static ClubMember member(Long userId, ClubRole role, String joinedAt) throws ReflectiveOperationException {
        ClubMember member = ClubMember.builder().clubId(10L).userId(userId).role(role).build();
        Field f = ClubMember.class.getDeclaredField("joinedAt");
        f.setAccessible(true);
        f.set(member, Instant.parse(joinedAt));
        return member;
    }

    private static User user(Long id) throws ReflectiveOperationException {
        User user = User.builder().handle("reader" + id).nickname("독자" + id).build();
        Field f = User.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(user, id);
        return user;
    }

    /** 호스트(1)와 멤버(2) 둘이 있는 모임 10 — 멤버 행을 돌려준다. */
    private ClubMember givenTwoMemberClub() {
        Club club = Club.builder().ownerId(1L).name("월요일의 데미안").joinCode("ABC234")
                .memberLimit((short) 10).startsAt(LocalDate.of(2026, 10, 3)).allowNudge(true).build();
        club.joinMember();
        club.joinMember();
        ClubMember member = ClubMember.builder().clubId(10L).userId(2L).role(ClubRole.MEMBER).build();
        when(clubRepository.findById(10L)).thenReturn(Optional.of(club));
        when(memberRepository.findByClubIdAndUserId(10L, 2L)).thenReturn(Optional.of(member));
        return member;
    }

    private static Club club(Long id) throws ReflectiveOperationException {
        Club club = Club.builder().ownerId(2L).name("모임 " + id).joinCode("ABC" + id)
                .memberLimit((short) 3).startsAt(LocalDate.of(2026, 10, 3)).allowNudge(true).build();
        Field f = Club.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(club, id);
        return club;
    }
}
