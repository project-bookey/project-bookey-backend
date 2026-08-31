package app.bookey.domain.reading;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 진척도 계산 (§F4).
 *
 * <pre>
 * 진행률          = current_page / total_pages
 * 필요 일일 페이스 = (total_pages - current_page) / 남은 일수
 * 실제 일일 페이스 = 최근 7일 읽은 페이지 / 7
 * 페이스 갭       = 실제 / 필요        // 1.0 이상이면 정상
 * 예상 완독일     = 오늘 + (남은 페이지 / 실제 일일 페이스)
 * </pre>
 */
public final class ProgressCalculator {

    public static final int PACE_WINDOW_DAYS = 7;

    private ProgressCalculator() {}

    public record Progress(
            int currentPage,
            int totalPages,
            Double completionRate,      // 0.0 ~ 1.0, 총 페이지 미상이면 null
            Double requiredDailyPace,   // 목표일 없으면 null
            Double actualDailyPace,
            Double paceGap,             // 목표일 없으면 null
            LocalDate estimatedFinishDate,
            Long daysSinceLastRead,
            LagLevel lagLevel
    ) {
        public int remainingPages() {
            return Math.max(0, totalPages - currentPage);
        }
    }

    public static Progress calculate(ReadingRecord record,
                                     int totalPages,
                                     long pagesReadInWindow,
                                     LocalDate today,
                                     Instant now) {
        int currentPage = record.getCurrentPage();
        Double completionRate = totalPages > 0
                ? Math.min(1.0, (double) currentPage / totalPages)
                : null;

        int remaining = Math.max(0, totalPages - currentPage);
        double actualPace = (double) pagesReadInWindow / PACE_WINDOW_DAYS;

        Double requiredPace = null;
        Double paceGap = null;
        if (record.getTargetFinishDate() != null && totalPages > 0) {
            long daysLeft = Math.max(1, ChronoUnit.DAYS.between(today, record.getTargetFinishDate()));
            requiredPace = (double) remaining / daysLeft;
            if (requiredPace > 0) {
                paceGap = actualPace / requiredPace;
            } else {
                paceGap = 1.0;   // 이미 목표 달성
            }
        }

        LocalDate estimated = null;
        if (remaining > 0 && actualPace > 0.01) {
            long daysNeeded = (long) Math.ceil(remaining / actualPace);
            estimated = today.plusDays(Math.min(daysNeeded, 3650));
        } else if (remaining == 0) {
            estimated = today;
        }

        Long daysSinceLastRead = record.getLastReadAt() == null
                ? null
                : Duration.between(record.getLastReadAt(), now).toDays();

        LagLevel lagLevel = record.getStatus().isNudgeable()
                ? LagLevel.evaluate(daysSinceLastRead == null ? 0 : daysSinceLastRead, paceGap)
                : LagLevel.L0_NORMAL;

        return new Progress(currentPage, totalPages, completionRate, requiredPace,
                actualPace, paceGap, estimated, daysSinceLastRead, lagLevel);
    }
}
