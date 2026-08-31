package app.bookey.domain.review;

import lombok.Getter;

/** 리뷰 검증 등급 (§F6). */
@Getter
public enum VerificationLevel {
    VERIFIED_FULL(0),      // 🟢 완독 검증
    VERIFIED_PARTIAL(1),   // 🔵 부분 검증
    UNVERIFIED(2),         // ⚪ 미검증
    FLAGGED(3);            // 🚩 의심

    /** 기본 정렬 우선순위 — 낮을수록 위 (§F6 정렬·노출 정책). */
    private final int sortOrder;

    VerificationLevel(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 검증 평점 계산에 포함되는가. */
    public boolean countsForVerifiedRating() {
        return this == VERIFIED_FULL;
    }

    public boolean hasBadge() {
        return this == VERIFIED_FULL || this == VERIFIED_PARTIAL;
    }
}
