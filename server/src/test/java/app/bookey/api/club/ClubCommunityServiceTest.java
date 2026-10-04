package app.bookey.api.club;

import app.bookey.api.club.dto.ClubCommunityDtos.ChatMessageView;
import app.bookey.api.club.dto.ClubCommunityDtos.MeetingView;
import app.bookey.api.club.dto.ClubCommunityDtos.UpsertMeetingRequest;
import app.bookey.api.social.WalletService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** 모임 최대 인원 — 정원이 차면 참여를 막고, 이미 참여한 사람보다 적게 줄이지 못한다.
 *  채팅 — 지금 클럽에 없는 사람(다시 참가하기 전의 나 포함)의 메시지는 '나간 멤버'로 보인다. */
class ClubCommunityServiceTest {

    private static final long CLUB_ID = 10L, MEETING_ID = 50L, HOST = 1L, ME = 2L;
    private static final Instant STARTS = Instant.now().plus(3, ChronoUnit.DAYS);

    private final ClubService clubService = mock(ClubService.class);
    private final ClubMeetingRepository meetings = mock(ClubMeetingRepository.class);
    private final ClubMeetingAttendeeRepository attendees = mock(ClubMeetingAttendeeRepository.class);
    private final ClubMemberRepository members = mock(ClubMemberRepository.class);
    private final ClubChatUnlockRepository unlocks = mock(ClubChatUnlockRepository.class);
    private final ClubChatMessageRepository messages = mock(ClubChatMessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ClubCommunityService service = new ClubCommunityService(clubService, mock(ClubRepository.class),
            members, unlocks, messages, mock(ClubChatReadRepository.class), meetings, attendees, users,
            mock(WalletService.class), mock(BookRepository.class));

    private static ClubMeeting meeting(Integer maxAttendees) {
        return new ClubMeeting(CLUB_ID, HOST, "3부까지 읽기", null, STARTS, null, "북카페", "서울", null, null, null,
                null, null, maxAttendees);
    }

    private static UpsertMeetingRequest request(Integer maxAttendees) {
        return new UpsertMeetingRequest("3부까지 읽기", null, STARTS, null, "북카페", "서울", null, null, null, null,
                null, maxAttendees);
    }

    private void given(ClubMeeting meeting, long attendeeCount, boolean meAttending) {
        when(meetings.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(meetings.findByIdForUpdate(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(attendees.countByMeetingId(MEETING_ID)).thenReturn(attendeeCount);
        when(attendees.existsById(new ClubMeetingAttendee.Key(MEETING_ID, ME))).thenReturn(meAttending);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @BeforeEach
    void host() {
        ClubMember host = ClubMember.builder().clubId(CLUB_ID).userId(HOST).role(ClubRole.HOST)
                .shareProgress(true).allowNudge(true).build();
        when(clubService.activeMember(CLUB_ID, HOST)).thenReturn(host);
    }

    @Test
    @DisplayName("정원이 차면 새로 참여할 수 없다 — MEETING_FULL")
    void fullMeetingRejectsNewAttendee() {
        given(meeting(3), 3, false);

        assertThatThrownBy(() -> service.attend(ME, CLUB_ID, MEETING_ID))
                .extracting(ClubCommunityServiceTest::codeOf)
                .isEqualTo(ErrorCode.MEETING_FULL);
        verify(attendees, never()).save(any());
    }

    @Test
    @DisplayName("이미 참여한 사람은 정원이 차도 그대로 — 다시 눌러도 같은 결과")
    void alreadyAttendingStaysWhenFull() {
        given(meeting(3), 3, true);

        service.attend(ME, CLUB_ID, MEETING_ID);

        verify(attendees, never()).save(any());
    }

    @Test
    @DisplayName("자리가 남았으면 참여하고, 제한이 없으면 늘 참여한다")
    void attendsWhenSeatsLeft() {
        given(meeting(3), 2, false);
        service.attend(ME, CLUB_ID, MEETING_ID);
        verify(attendees).save(any());

        given(meeting(null), 100, false);
        MeetingView view = service.attend(ME, CLUB_ID, MEETING_ID);
        assertThat(view.maxAttendees()).isNull();
    }

    @Test
    @DisplayName("이미 참여한 사람보다 적게 최대 인원을 줄일 수 없다")
    void cannotShrinkBelowAttendees() {
        given(meeting(5), 4, false);

        assertThatThrownBy(() -> service.updateMeeting(HOST, CLUB_ID, MEETING_ID, request(3)))
                .extracting(ClubCommunityServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ────────────────────────────── 채팅: 나간 멤버 ──────────────────────────────

    private static final long LEAVER = 3L, STRANGER = 4L;
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    @DisplayName("나간 멤버의 메시지는 이름을 숨기고 '나간 멤버'로 보인다")
    void leftMembersMessageIsAnonymous() {
        ClubMember me = member(ME, T0);
        ClubMember leaver = member(LEAVER, T0);
        leaver.leave();
        givenChat(me, List.of(message(1L, LEAVER, T0.plusSeconds(60))), List.of(leaver, me),
                List.of(user(LEAVER, "떠난이")));

        ChatMessageView view = service.chatMessages(ME, CLUB_ID, null).messages().get(0);

        assertThat(view.senderId()).isNull();
        assertThat(view.senderNickname()).isEqualTo("나간 멤버");
        assertThat(view.mine()).isFalse();
    }

    @Test
    @DisplayName("다시 참가한 나의 예전 메시지는 내 것이 아니라 '나간 멤버' — 다시 참가한 뒤 보낸 것만 내 것")
    void rejoinedMemberIsANewPerson() {
        ClubMember me = member(ME, T0);
        me.leave();
        me.rejoin(null);
        Instant rejoinedAt = me.getJoinedAt();
        givenChat(me, List.of(message(2L, ME, rejoinedAt.plusSeconds(30)), message(1L, ME, rejoinedAt.minusSeconds(3600))),
                List.of(me), List.of(user(ME, "나")));

        List<ChatMessageView> views = service.chatMessages(ME, CLUB_ID, null).messages();

        assertThat(views.get(0).mine()).isTrue();
        assertThat(views.get(0).senderNickname()).isEqualTo("나");
        assertThat(views.get(1).mine()).isFalse();
        assertThat(views.get(1).senderId()).isNull();
        assertThat(views.get(1).senderNickname()).isEqualTo("나간 멤버");
    }

    @Test
    @DisplayName("멤버 행도 계정도 없는 보낸 사람은 오류 없이 '나간 멤버'로 보인다")
    void unknownSenderDoesNotBreakChat() {
        ClubMember me = member(ME, T0);
        givenChat(me, List.of(message(1L, STRANGER, T0.plusSeconds(60))), List.of(me), List.of());

        ChatMessageView view = service.chatMessages(ME, CLUB_ID, null).messages().get(0);

        assertThat(view.senderId()).isNull();
        assertThat(view.senderNickname()).isEqualTo("나간 멤버");
    }

    private void givenChat(ClubMember me, List<ClubChatMessage> page, List<ClubMember> stints, List<User> authors) {
        when(clubService.activeMember(CLUB_ID, ME)).thenReturn(me);
        when(unlocks.existsByClubIdAndUserId(CLUB_ID, ME)).thenReturn(true);
        when(messages.findAllByClubIdOrderByIdDesc(eq(CLUB_ID), any())).thenReturn(page);
        when(members.findAllByClubIdAndUserIdIn(eq(CLUB_ID), any())).thenReturn(stints);
        when(users.findAllById(any())).thenReturn(authors);
    }

    private static ClubMember member(long userId, Instant joinedAt) {
        ClubMember member = ClubMember.builder().clubId(CLUB_ID).userId(userId).role(ClubRole.MEMBER).build();
        set(member, "joinedAt", joinedAt);
        return member;
    }

    private static ClubChatMessage message(long id, long senderId, Instant createdAt) {
        ClubChatMessage message = new ClubChatMessage(CLUB_ID, senderId, "안녕하세요");
        set(message, "id", id);
        set(message, "createdAt", createdAt);
        return message;
    }

    private static User user(long id, String nickname) {
        User user = User.builder().handle("handle" + id).nickname(nickname).build();
        set(user, "id", id);
        return user;
    }

    /** id 는 엔티티 자신에, 상위 클래스(BaseTimeEntity)에 있는 필드도 찾아 올라간다. */
    private static void set(Object target, String field, Object value) {
        try {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    var f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    // 상위 클래스에서 다시 찾는다.
                }
            }
            throw new IllegalArgumentException(field);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
