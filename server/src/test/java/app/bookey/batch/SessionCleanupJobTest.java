package app.bookey.batch;

import app.bookey.domain.reading.ReadingSession;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.reading.SessionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 오래 열린 세션 정리 — 독서 시간 4시간 상한과 오래 쉰 세션 닫기. */
class SessionCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final ReadingSessionRepository sessionRepository = mock(ReadingSessionRepository.class);
    private final SessionCleanupJob job =
            new SessionCleanupJob(sessionRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    private static ReadingSession startedAgo(Duration ago) {
        return ReadingSession.builder()
                .readingRecordId(1L).userId(1L).startedAt(NOW.minus(ago))
                .startPage(0).source(SessionSource.TIMER)
                .build();
    }

    @Test
    @DisplayName("쉬지 않고 4시간을 넘긴 세션은 4시간을 채운 때로 닫는다")
    void closesLongReading() {
        ReadingSession session = startedAgo(Duration.ofHours(5));
        when(sessionRepository.findStaleOpenSessions(NOW.minus(ReadingSession.MAX_SESSION)))
                .thenReturn(List.of(session));

        job.closeStaleSessions();

        assertThat(session.isOpen()).isFalse();
        assertThat(session.getDurationSec()).isEqualTo((int) Duration.ofHours(4).toSeconds());
        assertThat(session.getEndedAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("4시간 넘게 쉬고 있는 세션은 쉬기 시작한 시각에 닫는다")
    void closesLongPause() {
        ReadingSession session = startedAgo(Duration.ofHours(6));
        Instant pausedAt = NOW.minus(Duration.ofHours(5));
        session.pause(pausedAt);
        when(sessionRepository.findStaleOpenSessions(NOW.minus(ReadingSession.MAX_SESSION)))
                .thenReturn(List.of(session));

        job.closeStaleSessions();

        assertThat(session.isOpen()).isFalse();
        assertThat(session.getEndedAt()).isEqualTo(pausedAt);
        assertThat(session.getDurationSec()).isEqualTo((int) Duration.ofHours(1).toSeconds());
    }

    @Test
    @DisplayName("시작한 지 오래됐어도 독서 시간과 쉬는 시간이 4시간 안쪽이면 그대로 둔다")
    void keepsSessionWithShortActiveTime() {
        ReadingSession resumed = startedAgo(Duration.ofHours(5));
        resumed.pause(NOW.minus(Duration.ofHours(4)));
        resumed.resume(NOW.minus(Duration.ofHours(2))); // 독서 3시간
        ReadingSession pausing = startedAgo(Duration.ofHours(5));
        pausing.pause(NOW.minus(Duration.ofHours(2))); // 독서 3시간, 2시간째 쉬는 중
        when(sessionRepository.findStaleOpenSessions(NOW.minus(ReadingSession.MAX_SESSION)))
                .thenReturn(List.of(resumed, pausing));

        job.closeStaleSessions();

        assertThat(resumed.isOpen()).isTrue();
        assertThat(pausing.isOpen()).isTrue();
    }
}
