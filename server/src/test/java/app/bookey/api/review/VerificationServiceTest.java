package app.bookey.api.review;

import app.bookey.domain.reading.ReadingSession;
import app.bookey.domain.reading.SessionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationServiceTest {

    private static final Instant START = Instant.parse("2026-09-01T20:00:00Z");

    private ReadingSession closed(int startPage, int endPage, int minutes) {
        ReadingSession session = ReadingSession.builder()
                .readingRecordId(1L).userId(1L).startedAt(START)
                .startPage(startPage).source(SessionSource.TIMER).build();
        session.close(START.plus(Duration.ofMinutes(minutes)), endPage, 0.9, 10, null);
        return session;
    }

    @Test
    @DisplayName("겹치는 구간은 한 번만 센다 — 누적 커버리지")
    void mergesOverlappingRanges() {
        List<ReadingSession> sessions = List.of(
                closed(0, 100, 90),
                closed(80, 150, 60),     // 80~100 중복
                closed(200, 260, 50));

        // 0~150 (150쪽) + 200~260 (60쪽) = 210쪽
        assertThat(VerificationService.mergeUniquePages(sessions)).isEqualTo(210);
    }

    @Test
    @DisplayName("같은 구간을 반복해 읽어도 커버리지는 늘지 않는다")
    void repeatedSameRange() {
        List<ReadingSession> sessions = List.of(
                closed(0, 50, 40),
                closed(0, 50, 40),
                closed(0, 50, 40));

        assertThat(VerificationService.mergeUniquePages(sessions)).isEqualTo(50);
    }

    @Test
    @DisplayName("빨리 읽은 구간도 커버리지에 넣는다 — 어뷰징 감지 없음")
    void countsFastSessions() {
        ReadingSession fast = closed(0, 300, 5);  // 분당 60쪽

        assertThat(VerificationService.mergeUniquePages(List.of(fast))).isEqualTo(300);
    }
}
