package app.bookey.domain.social;

/** 팔로우가 성립한 경로 (§14.3) — 검색·팔로우 버튼은 없다. */
public enum FollowSource {
    /** 엽서에 답장이 성립해 자동 맞팔로우 */
    POSTCARD,
    /** 16자리 코드 · QR 로 팔로우 (지인) */
    CODE
}
