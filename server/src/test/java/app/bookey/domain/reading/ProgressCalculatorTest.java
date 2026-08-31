package app.bookey.domain.reading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final Instant NOW = TODAY.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

    private ReadingRecord record(int currentPage, LocalDate targetDate, Instant lastReadAt) {
        ReadingRecord record = ReadingRecord.builder()
                .userId(1L).bookId(1L).round((short) 1)
                .status(ReadingStatus.READING)
                .targetFinishDate(targetDate)
                .build();
        set(record, "currentPage", currentPage);
        set(record, "lastReadAt", lastReadAt);
        return record;
    }

    private void set(Object target, String field, Object value) {
        try {
            Field f = ReadingRecord.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("진행률과 남은 페이지를 계산한다")
    void calculatesCompletionRate() {
        var progress = ProgressCalculator.calculate(
                record(142, null, NOW), 636, 70, TODAY, NOW);

        assertThat(progress.completionRate()).isCloseTo(142.0 / 636, org.assertj.core.data.Offset.offset(0.001));
        assertThat(progress.remainingPages()).isEqualTo(494);
        assertThat(progress.actualDailyPace()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("목표일이 있으면 필요 페이스와 페이스 갭을 계산한다")
    void calculatesPaceGap() {
        // 남은 494쪽 / 남은 10일 = 필요 49.4쪽/일, 실제 10쪽/일 → 갭 약 0.2
        var progress = ProgressCalculator.calculate(
                record(142, TODAY.plusDays(10), NOW), 636, 70, TODAY, NOW);

        assertThat(progress.requiredDailyPace()).isCloseTo(49.4, org.assertj.core.data.Offset.offset(0.1));
        assertThat(progress.paceGap()).isCloseTo(0.202, org.assertj.core.data.Offset.offset(0.01));
        assertThat(progress.lagLevel()).isEqualTo(LagLevel.L3_SERIOUS);
    }

    @Test
    @DisplayName("총 페이지를 모르면 진행률은 null 이고 누적 시간만 의미를 갖는다")
    void unknownTotalPages() {
        var progress = ProgressCalculator.calculate(record(30, null, NOW), 0, 10, TODAY, NOW);

        assertThat(progress.completionRate()).isNull();
        assertThat(progress.paceGap()).isNull();
    }

    @ParameterizedTest(name = "{0}일 미독 → {1}")
    @CsvSource({
            "0, L0_NORMAL",
            "2, L0_NORMAL",
            "3, L1_CAUTION",
            "5, L2_DELAYED",
            "8, L3_SERIOUS",
            "15, L4_NEGLECTED"
    })
    @DisplayName("미독 일수로 지연 단계를 판정한다")
    void lagLevelByDays(long days, LagLevel expected) {
        Instant lastRead = NOW.minus(days, ChronoUnit.DAYS);
        var progress = ProgressCalculator.calculate(record(100, null, lastRead), 300, 0, TODAY, NOW);

        assertThat(progress.lagLevel()).isEqualTo(expected);
    }

    @Test
    @DisplayName("완독·하차 상태에서는 재촉하지 않는다")
    void closedRecordsAreNotNudged() {
        ReadingRecord record = record(300, null, NOW.minus(30, ChronoUnit.DAYS));
        record.finish(NOW, 300);

        var progress = ProgressCalculator.calculate(record, 300, 0, TODAY, NOW);

        assertThat(progress.lagLevel()).isEqualTo(LagLevel.L0_NORMAL);
    }
}
