package app.bookey.domain.club;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ClubActivitySessionTest {

    @Test
    @DisplayName("같이 읽기는 잰 만큼 기록한다")
    void recordsElapsed() {
        ClubActivitySession session = new ClubActivitySession(1L, 2L, 3L);
        session.end(session.getStartedAt().plus(Duration.ofMinutes(50)));

        assertThat(session.getDurationSec()).isEqualTo(50 * 60);
        assertThat(session.getEndedAt()).isEqualTo(session.getStartedAt().plus(Duration.ofMinutes(50)));
    }

    @Test
    @DisplayName("4시간을 넘겨 끝내면 4시간째에 끝난 것으로 4시간만 기록한다 — 며칠 켜 둔 같이 읽기도")
    void capsAtFourHours() {
        ClubActivitySession session = new ClubActivitySession(1L, 2L, 3L);
        session.end(session.getStartedAt().plus(Duration.ofDays(3)));

        assertThat(session.getDurationSec()).isEqualTo((int) Duration.ofHours(4).toSeconds());
        assertThat(session.getEndedAt()).isEqualTo(session.getStartedAt().plus(Duration.ofHours(4)));
    }

    @Test
    @DisplayName("바로 끝내도 최소 1초는 기록한다")
    void atLeastOneSecond() {
        ClubActivitySession session = new ClubActivitySession(1L, 2L, 3L);
        session.end(session.getStartedAt());

        assertThat(session.getDurationSec()).isEqualTo(1);
    }
}
