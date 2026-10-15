package app.bookey.domain.novel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 홈 열매 밭 새로고침의 하루 무료 횟수. 엽서처럼 KST 자정에 다시 찬다.
 * 자정에 따로 지우지 않고, 횟수를 센 날(countedOn)이 오늘이 아니면 읽을 때든 쓸 때든 쓴 횟수를 0 으로 본다.
 */
public record NovelRefreshQuota(int dailyFree, int usedToday, LocalDate countedOn) {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 지금이 KST 로 어느 날인지. */
    public static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, KST);
    }

    /** 무료 횟수가 다시 차는 때 — 다음 KST 자정. */
    public static Instant nextResetAfter(Instant now) {
        return today(now).plusDays(1).atStartOfDay(KST).toInstant();
    }

    /** 오늘 쓴 횟수. 센 날이 오늘이 아니면 0 이다. */
    public int usedOn(LocalDate today) {
        return today.equals(countedOn) ? usedToday : 0;
    }

    public int leftOn(LocalDate today) {
        return Math.max(0, dailyFree - usedOn(today));
    }

    public boolean canRefreshOn(LocalDate today) {
        return leftOn(today) > 0;
    }

    /** 하나를 쓴 뒤의 횟수. 날이 바뀌었으면 0 에서 다시 센다. */
    public NovelRefreshQuota useOneOn(LocalDate today) {
        if (!canRefreshOn(today)) {
            throw new IllegalStateException("오늘 남은 무료 새로고침이 없다");
        }
        return new NovelRefreshQuota(dailyFree, usedOn(today) + 1, today);
    }
}
