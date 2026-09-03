package app.bookey.domain.post;

/** 독후감 공개 범위. 실제 읽기 규칙은 작성자 여부까지 보는 {@link Post#isReadableBy(Long)} 가 정한다. */
public enum PostVisibility {
    PUBLIC,
    LINK,
    PRIVATE
}
