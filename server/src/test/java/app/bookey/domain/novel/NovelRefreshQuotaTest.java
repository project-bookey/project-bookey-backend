package app.bookey.domain.novel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class NovelRefreshQuotaTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test @DisplayName("하루 두 번까지 쓰고, 다 쓰면 더 쓸 수 없다")
    void usesUpToDailyFree() {
        NovelRefreshQuota fresh = new NovelRefreshQuota(2, 0, TODAY);
        NovelRefreshQuota usedOnce = fresh.useOneOn(TODAY);
        NovelRefreshQuota usedTwice = usedOnce.useOneOn(TODAY);

        assertThat(fresh.leftOn(TODAY)).isEqualTo(2);
        assertThat(usedOnce.leftOn(TODAY)).isEqualTo(1);
        assertThat(usedTwice.leftOn(TODAY)).isZero();
        assertThat(usedTwice.canRefreshOn(TODAY)).isFalse();
        assertThatThrownBy(() -> usedTwice.useOneOn(TODAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("횟수를 센 날이 오늘이 아니면 쓴 횟수를 0으로 보고, 쓰면 오늘부터 다시 센다")
    void countsFromZeroOnNewDay() {
        NovelRefreshQuota usedUpYesterday = new NovelRefreshQuota(2, 2, TODAY.minusDays(1));

        assertThat(usedUpYesterday.usedOn(TODAY)).isZero();
        assertThat(usedUpYesterday.leftOn(TODAY)).isEqualTo(2);
        NovelRefreshQuota usedToday = usedUpYesterday.useOneOn(TODAY);
        assertThat(usedToday.usedToday()).isEqualTo(1);
        assertThat(usedToday.countedOn()).isEqualTo(TODAY);
    }

    @Test @DisplayName("오늘은 KST 날짜로 센다 — UTC 15시가 지나면 KST 로는 다음 날이다")
    void todayIsKstDate() {
        assertThat(NovelRefreshQuota.today(Instant.parse("2026-10-15T14:59:59Z"))).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(NovelRefreshQuota.today(Instant.parse("2026-10-15T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test @DisplayName("다시 차는 때는 다음 KST 자정이고, 자정 정각이면 그다음 자정이다")
    void resetsAtNextKstMidnight() {
        Instant kstNoon = Instant.parse("2026-10-15T03:00:00Z");
        Instant kstOneSecondBeforeMidnight = Instant.parse("2026-10-15T14:59:59Z");
        Instant kstMidnight = Instant.parse("2026-10-15T15:00:00Z");

        assertThat(NovelRefreshQuota.nextResetAfter(kstNoon)).isEqualTo(kstMidnight);
        assertThat(NovelRefreshQuota.nextResetAfter(kstOneSecondBeforeMidnight)).isEqualTo(kstMidnight);
        assertThat(NovelRefreshQuota.nextResetAfter(kstMidnight)).isEqualTo(Instant.parse("2026-10-16T15:00:00Z"));
    }
}
