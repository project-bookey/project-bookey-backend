package app.bookey.domain.wallet;

public enum SubscriptionStore {
    APPLE, GOOGLE, TOSS,
    /** 관리자 수동 지급 — 스토어 IAP 검증이 붙기 전의 MVP 경로 */
    ADMIN
}
