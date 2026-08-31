package app.bookey.domain.reading;

public enum SessionSource {
    /** 타이머 모드 — 검증 등급에 온전히 반영 */
    TIMER,
    /** 수동 기록 — 시간의 40%만 인정(§8.2) */
    MANUAL;

    public double verificationWeight() {
        return this == TIMER ? 1.0 : 0.4;
    }
}
