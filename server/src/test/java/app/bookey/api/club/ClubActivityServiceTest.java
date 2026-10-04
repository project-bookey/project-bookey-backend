package app.bookey.api.club;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.club.*;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 같이 읽기 — 모임에서 시작하려면 그 모임 참여자여야 한다. */
class ClubActivityServiceTest {

    private static final long CLUB_ID = 10L, MEETING_ID = 50L, ME = 2L;

    private final ClubMeetingRepository meetings = mock(ClubMeetingRepository.class);
    private final ClubMeetingAttendeeRepository attendees = mock(ClubMeetingAttendeeRepository.class);
    private final ClubActivitySessionRepository sessions = mock(ClubActivitySessionRepository.class);
    private final ClubActivityService service = new ClubActivityService(mock(ClubService.class), mock(ClubRepository.class),
            meetings, attendees, sessions, mock(ClubActivityCardRepository.class), mock(UserRepository.class),
            Clock.systemUTC());

    @BeforeEach
    void meeting() {
        ClubMeeting meeting = new ClubMeeting(CLUB_ID, 1L, "3부까지 읽기", null, Instant.now(), null, "북카페", "서울",
                null, null, null, null, null, null);
        when(meetings.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
        when(sessions.findByClubIdAndUserIdAndEndedAtIsNull(CLUB_ID, ME)).thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("모임에 참여하지 않았으면 같이 읽기를 시작할 수 없다 — MEETING_NOT_ATTENDING")
    void nonAttendeeCannotStart() {
        when(attendees.existsById(new ClubMeetingAttendee.Key(MEETING_ID, ME))).thenReturn(false);

        assertThatThrownBy(() -> service.start(ME, CLUB_ID, MEETING_ID))
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEETING_NOT_ATTENDING);
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("모임 참여자는 같이 읽기를 시작한다")
    void attendeeStarts() {
        when(attendees.existsById(new ClubMeetingAttendee.Key(MEETING_ID, ME))).thenReturn(true);

        service.start(ME, CLUB_ID, MEETING_ID);

        verify(sessions).save(any());
    }
}
