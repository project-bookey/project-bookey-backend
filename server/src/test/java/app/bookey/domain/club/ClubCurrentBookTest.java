package app.bookey.domain.club;

import app.bookey.domain.club.ClubCurrentBook.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 모임의 지금 읽는 책 — 다가오는 만남의 책을 따른다. */
class ClubCurrentBookTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    /** KST 날짜·시각의 만남. */
    private static Slot slot(String kstDateTime, Long bookId) {
        return new Slot(java.time.LocalDateTime.parse(kstDateTime).atZone(KST).toInstant(), true, bookId);
    }

    private static Long pick(Slot... slots) {
        return ClubCurrentBook.pick(List.of(slots), TODAY, KST);
    }

    @Test
    @DisplayName("다가오는 만남 중 가장 가까운 만남의 책을 따른다")
    void picksNearestUpcoming() {
        assertThat(pick(slot("2026-10-20T19:00", 3L), slot("2026-10-10T19:00", 2L), slot("2026-09-20T19:00", 1L)))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("만남 날 하루 동안은 그 만남이 다가오는 만남이다 — 오전에 끝난 만남도 그날은 지금 책")
    void meetingDayStillCounts() {
        assertThat(pick(slot("2026-10-03T10:00", 2L), slot("2026-10-20T19:00", 3L))).isEqualTo(2L);
    }

    @Test
    @DisplayName("다가오는 만남이 없으면 가장 최근에 지난 만남의 책")
    void fallsBackToLatestPast() {
        assertThat(pick(slot("2026-09-01T19:00", 1L), slot("2026-09-20T19:00", 2L))).isEqualTo(2L);
    }

    @Test
    @DisplayName("책을 고르지 않은 만남과 취소된 만남은 보지 않는다")
    void skipsBooklessAndCancelled() {
        Slot cancelled = new Slot(Instant.parse("2026-10-05T10:00:00Z"), false, 9L);
        assertThat(pick(slot("2026-10-04T19:00", null), cancelled, slot("2026-10-30T19:00", 3L))).isEqualTo(3L);
    }

    @Test
    @DisplayName("책을 고른 만남이 하나도 없으면 null — 지금 책을 그대로 둔다")
    void noBookMeansNull() {
        assertThat(pick(slot("2026-10-04T19:00", null))).isNull();
        assertThat(pick()).isNull();
    }
}
