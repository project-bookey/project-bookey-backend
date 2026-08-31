package app.bookey.domain.reading;

/** 서재 상태 (§F2). */
public enum ReadingStatus {
    WANT_TO_READ,
    READING,
    PAUSED,
    FINISHED,
    ABANDONED;

    /** 재촉 알림 대상인가. PAUSED/FINISHED/ABANDONED 는 재촉하지 않는다. */
    public boolean isNudgeable() {
        return this == READING;
    }

    public boolean isClosed() {
        return this == FINISHED || this == ABANDONED;
    }
}
