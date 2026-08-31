package app.bookey.domain.club;

/** 스포일러 가드 수준 (§12.3). */
public enum SpoilerLevel {
    /** 항상 노출 */
    NONE,
    /** anchor_page 기준 — 내 진도보다 앞서면 가림 */
    PAGE,
    /** 완독자에게만 노출 */
    BOOK
}
