package app.bookey.domain.social;

public enum PostcardStatus {
    /** 발송됨 — 답장 대기. 거절 통보는 없다(§14.2). */
    SENT,
    /** 답장 성립 — 맞팔로우 완료. */
    REPLIED
}
