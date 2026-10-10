package app.bookey.domain.admin;

/** 신고·검수 대상 종류. 앞의 여섯은 콘텐츠(관리자 콘텐츠 검수에서 직접 다룬다), CLUB·USER 는 모임·회원 자체. */
public enum ModerationSource {
    REVIEW,
    POST,
    CLUB_POST,
    CLUB,
    USER,
    /** 독후감 댓글 */
    POST_COMMENT,
    /** 리뷰 댓글 */
    REVIEW_COMMENT,
    /** 한 줄평 */
    BOOK_REMARK
}
