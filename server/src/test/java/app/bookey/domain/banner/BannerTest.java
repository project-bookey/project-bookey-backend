package app.bookey.domain.banner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BannerTest {

    private Banner banner(Instant startsAt, Instant endsAt) {
        return Banner.builder()
                .title("가을 독서 챌린지")
                .sortOrder(0)
                .enabled(true)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }

    @Test
    @DisplayName("시작 시각은 포함, 종료 시각은 제외한다")
    void activeWindowIsHalfOpen() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-09-21T00:00:00Z");
        Banner b = banner(start, end);

        assertThat(b.isActiveAt(start)).isTrue();
        assertThat(b.isActiveAt(end.minusSeconds(1))).isTrue();
        assertThat(b.isActiveAt(end)).isFalse();
        assertThat(b.isActiveAt(start.minusSeconds(1))).isFalse();
    }
}
