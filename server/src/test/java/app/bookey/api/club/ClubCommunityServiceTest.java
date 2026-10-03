package app.bookey.api.club;

import app.bookey.api.club.dto.ClubCommunityDtos.MeetingView;
import app.bookey.api.club.dto.ClubCommunityDtos.UpsertMeetingRequest;
import app.bookey.api.social.WalletService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 모임 최대 인원 — 정원이 차면 참여를 막고, 이미 참여한 사람보다 적게 줄이지 못한다. */
class ClubCommunityServiceTest {

    private static final long CLUB_ID = 10L, MEETING_ID = 50L, HOST = 1L, ME = 2L;
    private static final Instant STARTS = Instant.now().plus(3, ChronoUnit.DAYS);

    private final ClubService clubService = mock(ClubService.class);
    private final ClubMeetingRepository meetings = mock(ClubMeetingRepository.class);
    private final ClubMeetingAttendeeRepository attendees = mock(ClubMeetingAttendeeRepository.class);
    private final ClubCommunityService service = new ClubCommunityService(clubService, mock(ClubRepository.class),
            mock(ClubMemberRepository.class), mock(ClubChatUnlockRepository.class), mock(ClubChatMessageRepository.class),
            mock(ClubChatReadRepository.class), meetings, attendees, mock(UserRepository.class),
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
}
