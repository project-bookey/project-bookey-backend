package app.bookey.domain.reading;

import lombok.Getter;

/** 지연 단계 (§F4). 재촉 강도의 입력값. */
@Getter
public enum LagLevel {
    L0_NORMAL(0),
    L1_CAUTION(1),
    L2_DELAYED(2),
    L3_SERIOUS(3),
    L4_NEGLECTED(4);

    private final int level;

    LagLevel(int level) {
        this.level = level;
    }

    /**
     * @param daysSinceLastRead 마지막 독서 이후 경과일
     * @param paceGap           실제 페이스 / 필요 페이스 (목표일이 없으면 null)
     */
    public static LagLevel evaluate(long daysSinceLastRead, Double paceGap) {
        if (daysSinceLastRead >= 15) {
            return L4_NEGLECTED;
        }
        if (daysSinceLastRead >= 8 || lt(paceGap, 0.3)) {
            return L3_SERIOUS;
        }
        if (daysSinceLastRead >= 5 || between(paceGap, 0.3, 0.6)) {
            return L2_DELAYED;
        }
        if (daysSinceLastRead >= 3 || between(paceGap, 0.6, 0.9)) {
            return L1_CAUTION;
        }
        return L0_NORMAL;
    }

    private static boolean lt(Double value, double bound) {
        return value != null && value < bound;
    }

    private static boolean between(Double value, double lower, double upper) {
        return value != null && value >= lower && value < upper;
    }

    public boolean needsNotification() {
        return this != L0_NORMAL;
    }
}
