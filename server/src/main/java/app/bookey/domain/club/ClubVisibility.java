package app.bookey.domain.club;

/** 모임 공개 범위 (§12.1). */
public enum ClubVisibility {
    /** 코드를 아는 사람만. 목록에 노출되지 않음 (기본값) */
    CODE_ONLY,
    /** 링크 보유자 참가 + 초대 랜딩에서 미리보기 */
    LINK,
    /** 발견 탭에 노출, 누구나 참가 */
    PUBLIC;

    public boolean isListable() {
        return this == PUBLIC;
    }
}
