package app.bookey.domain.novel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class NovelRefreshQuotaTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

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

    @Test @DisplayName("광고 횟수도 같은 규칙으로 센다 — 하루 세 번까지 쓰고, 날이 바뀌면 다시 세 번이다")
    void adCountFollowsSameRule() {
        NovelRefreshQuota usedUp = new NovelRefreshQuota(3, 0, TODAY).useOneOn(TODAY).useOneOn(TODAY).useOneOn(TODAY);

        assertThat(usedUp.leftOn(TODAY)).isZero();
        assertThatThrownBy(() -> usedUp.useOneOn(TODAY)).isInstanceOf(IllegalStateException.class);
        assertThat(usedUp.leftOn(TODAY.plusDays(1))).isEqualTo(3);
    }

    @Test @DisplayName("피드 자리는 무료·광고 횟수를 한 날짜로 센다 — 날이 바뀐 뒤 광고를 쓰면 어제 무료 횟수가 오늘로 넘어오지 않는다")
    void adUseOnNewDayDropsYesterdaysFreeCount() {
        NovelHomeFeed feed = usedUpOn(YESTERDAY);

        feed.refreshWithAd(List.of(9L), 3, TODAY);

        assertThat(feed.freeQuota(2).leftOn(TODAY)).isEqualTo(2);
        assertThat(feed.adQuota(3).leftOn(TODAY)).isEqualTo(2);
        assertThat(feed.getFreeResetDate()).isEqualTo(TODAY);
    }

    @Test @DisplayName("날이 바뀐 뒤 무료를 쓰면 어제 광고 횟수도 오늘로 넘어오지 않는다")
    void freeUseOnNewDayDropsYesterdaysAdCount() {
        NovelHomeFeed feed = usedUpOn(YESTERDAY);

        feed.refreshWithFree(List.of(9L), 2, TODAY);

        assertThat(feed.freeQuota(2).leftOn(TODAY)).isEqualTo(1);
        assertThat(feed.adQuota(3).leftOn(TODAY)).isEqualTo(3);
    }

    @Test @DisplayName("결제한 새로고침은 날이 바뀌어도 남고 하나씩 쓴다 — 없으면 쓸 수 없다")
    void paidRefreshesSurviveDayChange() {
        NovelHomeFeed feed = usedUpOn(YESTERDAY);
        feed.addPaidRefresh();

        feed.refreshWithFree(List.of(9L), 2, TODAY);
        assertThat(feed.getPaidRefreshes()).isEqualTo(1);
        feed.refreshWithPaid(List.of(10L));

        assertThat(feed.getPaidRefreshes()).isZero();
        assertThat(feed.getNovelIds()).containsExactly(10L);
        assertThatThrownBy(() -> feed.refreshWithPaid(List.of(11L))).isInstanceOf(IllegalStateException.class);
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

    /** 그날 무료 두 번·광고 세 번을 다 쓴 피드 자리. */
    private static NovelHomeFeed usedUpOn(LocalDate day) {
        NovelHomeFeed feed = new NovelHomeFeed(1L, day);
        feed.refreshWithFree(List.of(1L), 2, day);
        feed.refreshWithFree(List.of(2L), 2, day);
        for (long id = 3; id <= 5; id++) feed.refreshWithAd(List.of(id), 3, day);
        return feed;
    }
}
