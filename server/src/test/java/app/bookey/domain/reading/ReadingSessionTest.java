package app.bookey.domain.reading;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static Instant at(int minutes) {
        return START.plus(Duration.ofMinutes(minutes));
    }

    @Test
    @DisplayName("쉬었다 이어서 읽으면 쉰 시간은 독서 시간에서 빠진다")
    void pausedTimeIsExcluded() {
        ReadingSession session = session(0);
        session.pause(at(20));
        session.resume(at(35));
        session.pause(at(50));
        session.resume(at(55));
        session.close(at(70), 40, null, 0, null);

        assertThat(session.getPausedSec()).isEqualTo(20 * 60);
        assertThat(session.getDurationSec()).isEqualTo(50 * 60);
        assertThat(session.getEndedAt()).isEqualTo(at(70));
        assertThat(session.isPaused()).isFalse();
    }

    @Test
    @DisplayName("쉬는 중에 끝내면 쉬기 시작한 뒤의 시간은 세지 않는다")
    void closeWhilePaused() {
        ReadingSession session = session(0);
        session.pause(at(30));
        assertThat(session.activeDuration(at(90))).isEqualTo(Duration.ofMinutes(30));

        session.close(at(90), 20, null, 0, null);

        assertThat(session.getDurationSec()).isEqualTo(30 * 60);
        assertThat(session.getPausedAt()).isNull();
        assertThat(session.getPausedSec()).isEqualTo(60 * 60);
    }

    @Test
    @DisplayName("쉬기·이어서를 두 번 눌러도 처음 누른 때가 기준이다")
    void pauseAndResumeAreIdempotent() {
        ReadingSession session = session(0);
        session.resume(at(5)); // 쉬는 중이 아니면 그대로
        session.pause(at(10));
        session.pause(at(15));
        session.resume(at(20));
        session.resume(at(25));

        assertThat(session.getPausedSec()).isEqualTo(10 * 60);
        assertThat(session.activeDuration(at(30))).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    @DisplayName("끝난 세션은 쉬거나 이어서 할 수 없다")
    void closedSessionCannotPause() {
        ReadingSession session = session(0);
        session.close(at(10), 5, null, 0, null);

        assertThatThrownBy(() -> session.pause(at(11)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SESSION_ALREADY_CLOSED));
        assertThatThrownBy(() -> session.resume(at(11))).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("쉰 시간을 빼고도 4시간을 넘기면 4시간을 채운 때에 끝난 것으로 자른다")
    void capAppliesToActiveTime() {
        ReadingSession session = session(0);
        session.pause(at(60));
        session.resume(at(120)); // 1시간 쉼
        session.close(at(6 * 60), 50, null, 0, null); // 6시간 중 5시간 독서

        assertThat(session.getDurationSec()).isEqualTo((int) Duration.ofHours(4).toSeconds());
        assertThat(session.getEndedAt()).isEqualTo(at(5 * 60));
    }

    @Test
    @DisplayName("지금 읽는 중 — 쉬는 세션과 독서 시간이 4시간을 넘긴 세션은 빠진다")
    void readingNow() {
        ReadingSession reading = session(0);
        assertThat(reading.isReadingNow(at(30))).isTrue();
        assertThat(reading.isReadingNow(at(4 * 60))).isFalse();

        ReadingSession paused = session(0);
        paused.pause(at(10));
        assertThat(paused.isReadingNow(at(30))).isFalse();
        paused.resume(at(3 * 60));
        // 시작한 지 4시간 반이지만 독서 시간은 1시간 40분
        assertThat(paused.isReadingNow(at(4 * 60 + 30))).isTrue();
    }
}
