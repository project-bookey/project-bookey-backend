package app.bookey.domain.club;

/** 토론 글 타입 (§12.3). */
public enum ClubPostType {
    DISCUSSION,
    QUESTION,
    QUOTE,
    /** 호스트/모더레이터만 작성, 상단 고정 */
    NOTICE,
    /** 체크포인트 마감 시 자동 생성되는 회고 스레드 */
    CHECKPOINT,
    /** 읽기로그 조각 — 독서 세션 끝에 남기는 사진 한 장 + 한 줄. 토론 목록에는 섞지 않는다. */
    LOG;

    public boolean requiresModerator() {
        return this == NOTICE;
    }

    public boolean requiresAnchorPage() {
        return this == QUOTE;
    }
}
