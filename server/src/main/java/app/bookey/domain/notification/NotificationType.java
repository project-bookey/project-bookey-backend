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

    // 소셜 즉시 알림 — 푸시 설정과 별개로 인앱 목록에는 항상 남긴다.
    POSTCARD_RECEIVED(false),
    POSTCARD_REPLIED(false),
    /** 누군가 나를 팔로우했다(한 방향) */
    FOLLOWED(false),
    /** 팔로우가 맞팔로우로 이어졌다 */
    FOLLOW_CONNECTED(false),
    CHAT_MESSAGE(false),
    POST_LIKED(false),
    POST_COMMENTED(false),
    /** 고객문의에 관리자가 답변했다 */
    INQUIRY_ANSWERED(false),
    /** 광고성 정보 수신 동의·철회 처리 결과(정보통신망법 제50조 ⑧) */
    CONSENT_RESULT(false),
    /** 운영 정책에 따른 경고·이용 제한 안내 — 사유와 기간을 알린다(이용약관 제10조 ②) */
    SANCTION_NOTICE(false),

    // 모임 (모임당 일 1건 / 전체 일 3건 — 개인 한도와 별도)
    CLUB_CHECKPOINT_DUE(true),
    CLUB_CHECKPOINT_RESULT(true),
    CLUB_OVERTAKEN(true),
    CLUB_FALLBEHIND(true),
    CLUB_NEW_POST(true),
    CLUB_NUDGE(true),
    CLUB_ENDED(true),
    // 폐기됨(모임 노트북) — 옛 알림 행 호환용
    CLUB_NOTE_PAGE(true),
    /** 일요일 밤 — 이번 주 읽기로그 카드가 준비됐다는 알림 */
    CLUB_WEEKLY_LOG(true);

    private final boolean clubScoped;

    NotificationType(boolean clubScoped) {
        this.clubScoped = clubScoped;
    }

    /** 성취·완독 알림은 총량 제한에서 제외한다(사용자가 반기는 알림). */
    public boolean bypassesCap() {
        return this == ACHIEVEMENT;
    }
}
