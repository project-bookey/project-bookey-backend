package app.bookey.domain.social;

/** 팔로우가 성립한 경로 (§14.3) — 지금은 팔로우 버튼(BUTTON) 하나뿐이다. */
public enum FollowSource {
    /** 프로필·피드·댓글의 팔로우 버튼 */
    BUTTON,
    /** 폐기됨 — 엽서 답장 시 자동 맞팔로우하던 시절의 옛 행 호환용 */
    POSTCARD,
    /** 폐기됨 — 16자리 코드 팔로우 시절의 옛 행 호환용 */
    CODE
}
