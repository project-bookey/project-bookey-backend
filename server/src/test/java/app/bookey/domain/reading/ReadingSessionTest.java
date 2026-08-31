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
        assertThat(session.isCountedForVerification()).isTrue();
        assertThat(session.getAbuseFlags()).isEmpty();
    }

    @Test
    @DisplayName("포그라운드 비율이 낮고 상호작용이 없으면 타이머 방치로 판정한다")
    void idleTimerIsNotCounted() {
        ReadingSession session = session(100);
        session.close(START.plus(Duration.ofMinutes(60)), 105, 0.1, 0, null);

        assertThat(session.getAbuseFlags()).contains("idle_timer");
        assertThat(session.isCountedForVerification()).isFalse();
    }

    @Test
    @DisplayName("분당 5쪽을 초과하면 비정상 속도로 검증에서 제외한다")
    void abnormalSpeedIsExcluded() {
        ReadingSession session = session(0);
        // 10분에 200쪽 = 분당 20쪽
        session.close(START.plus(Duration.ofMinutes(10)), 200, 0.9, 30, null);

        assertThat(session.getAbuseFlags()).contains("abnormal_speed");
        assertThat(session.isCountedForVerification()).isFalse();
    }

    @Test
    @DisplayName("4시간을 넘긴 세션은 자동 종료되고 의심 플래그가 붙는다")
    void autoClosesLongSession() {
        ReadingSession session = session(0);
        session.close(START.plus(Duration.ofHours(9)), 50, 0.9, 5, null);

        assertThat(session.getDurationSec()).isEqualTo((int) Duration.ofHours(4).toSeconds());
        assertThat(session.getAbuseFlags()).contains("suspect_idle");
        assertThat(session.isCountedForVerification()).isFalse();
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
