package app.bookey.domain.reading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingSessionTest {

    private static final Instant START = Instant.parse("2026-09-01T20:00:00Z");

    private ReadingSession session(int startPage) {
        return ReadingSession.builder()
                .readingRecordId(1L).userId(1L).startedAt(START)
                .startPage(startPage).source(SessionSource.TIMER)
                .build();
    }

    @Test
    @DisplayName("정상 세션은 검증에 반영된다")
    void normalSession() {
        ReadingSession session = session(100);
        session.close(START.plus(Duration.ofMinutes(30)), 130, 0.95, 12, null);

        assertThat(session.getDurationSec()).isEqualTo(1800);
        assertThat(session.readPages()).isEqualTo(30);
        assertThat(session.verifiedDurationSec()).isEqualTo(1800);
    }

    @Test
    @DisplayName("한 번에 많이 읽어도(분당 5쪽 초과) 시간을 그대로 인정한다 — 어뷰징 감지 없음")
    void fastReadingIsCounted() {
        ReadingSession session = session(0);
        // 10분에 200쪽 = 분당 20쪽
        session.close(START.plus(Duration.ofMinutes(10)), 200, 0.9, 30, null);

        assertThat(session.readPages()).isEqualTo(200);
        assertThat(session.verifiedDurationSec()).isEqualTo(600);
    }

    @Test
    @DisplayName("앱을 거의 안 봤어도 타이머 시간을 그대로 인정한다")
    void backgroundTimerIsCounted() {
        ReadingSession session = session(100);
        session.close(START.plus(Duration.ofMinutes(60)), 105, 0.1, 0, null);

        assertThat(session.verifiedDurationSec()).isEqualTo(3600);
    }

    @Test
    @DisplayName("4시간을 넘긴 세션은 4시간으로 잘라 닫고, 그 4시간은 인정한다")
    void autoClosesLongSession() {
        ReadingSession session = session(0);
        session.close(START.plus(Duration.ofHours(9)), 50, 0.9, 5, null);

        int fourHours = (int) Duration.ofHours(4).toSeconds();
        assertThat(session.getDurationSec()).isEqualTo(fourHours);
        assertThat(session.verifiedDurationSec()).isEqualTo(fourHours);
    }

    @Test
    @DisplayName("수동 기록은 시간의 40%만 검증에 인정된다")
    void manualSessionWeight() {
        ReadingSession session = ReadingSession.builder()
                .readingRecordId(1L).userId(1L).startedAt(START)
                .startPage(0).source(SessionSource.MANUAL).build();
        session.closeManual(START.plus(Duration.ofMinutes(100)), 6000, 100, null);

        assertThat(session.verifiedDurationSec()).isEqualTo(2400);
    }
}
