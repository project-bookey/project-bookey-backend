package app.bookey.domain.notification;

import lombok.Getter;

/** 알림 종류 (§F5 알림 종류 표 + §12.4 모임 알림). */
@Getter
public enum NotificationType {
    // 개인 (일 2건 / 주 7건 상한)
    HABIT(false),
    LAG(false),
    MICRO_MISSION(false),
    STREAK(false),
    ALMOST_DONE(false),
    ACHIEVEMENT(false),
    CLEANUP(false),

    // 모임 (모임당 일 1건 / 전체 일 3건 — 개인 한도와 별도)
    CLUB_CHECKPOINT_DUE(true),
    CLUB_CHECKPOINT_RESULT(true),
    CLUB_OVERTAKEN(true),
    CLUB_FALLBEHIND(true),
    CLUB_NEW_POST(true),
    CLUB_NUDGE(true),
    CLUB_ENDED(true);

    private final boolean clubScoped;

    NotificationType(boolean clubScoped) {
        this.clubScoped = clubScoped;
    }

    /** 성취·완독 알림은 총량 제한에서 제외한다(사용자가 반기는 알림). */
    public boolean bypassesCap() {
        return this == ACHIEVEMENT;
    }
}
