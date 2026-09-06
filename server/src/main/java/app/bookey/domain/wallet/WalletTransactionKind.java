package app.bookey.domain.wallet;

/** 재화 원장 이벤트 종류. */
public enum WalletTransactionKind {
    /** 스토어 인앱 구매 (후속 — IAP 검증) */
    PURCHASE,
    /** 구독 월 지급 — 엽서 50 · 우표 30 */
    SUBSCRIPTION_GRANT,
    /** YES24 제휴 적립 (후속 — 계약 체결 뒤) */
    AFFILIATE,
    /** 책갈피 → 엽서 교환 (1:1) */
    EXCHANGE_POSTCARD,
    /** 책갈피 → 우표 교환 (2:1) */
    EXCHANGE_STAMP,
    /** 엽서 발송 — 무료 일일분 사용 (잔액 변화 없음) */
    SEND_POSTCARD_FREE,
    /** 엽서 발송 — 보유 엽서 차감 */
    SEND_POSTCARD,
    /** 발송 시 우표 동봉 차감 */
    ATTACH_STAMP,
    /** 답장 우표 차감 */
    REPLY_STAMP,
    /** 관리자 수동 조정 */
    ADMIN_ADJUST
}
