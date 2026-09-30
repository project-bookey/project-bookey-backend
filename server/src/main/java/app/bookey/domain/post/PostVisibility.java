package app.bookey.domain.post;

/**
 * 독후감 공개 범위. 실제 읽기 규칙은 작성자·모임 멤버 여부까지 보는 {@link Post#isReadableBy(Long, boolean)} 가 정한다.
 * 모임 밖 글은 PUBLIC·LINK·PRIVATE, 모임 글은 PUBLIC(모임+광장)·CLUB(모임만) 만 쓴다 — {@link PostRules} 가 검사한다.
 */
public enum PostVisibility {
    PUBLIC,
    LINK,
    PRIVATE,
    /** 모임 멤버만 — 모임 글에서만 쓴다 */
    CLUB
}
