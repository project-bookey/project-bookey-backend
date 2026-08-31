package app.bookey.domain.club;

/** 토론 글 타입 (§12.3). */
public enum ClubPostType {
    DISCUSSION,
    QUESTION,
    QUOTE,
    /** 호스트/모더레이터만 작성, 상단 고정 */
    NOTICE,
    /** 체크포인트 마감 시 자동 생성되는 회고 스레드 */
    CHECKPOINT;

    public boolean requiresModerator() {
        return this == NOTICE;
    }

    public boolean requiresAnchorPage() {
        return this == QUOTE;
    }
}
