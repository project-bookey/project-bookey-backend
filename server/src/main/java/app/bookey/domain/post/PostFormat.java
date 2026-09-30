package app.bookey.domain.post;

/** 독후감 작성 방식. 처음에 고르고 바꿀 수 없다. */
public enum PostFormat {
    /** 마크다운 본문 + 사진·오려둔 문장 */
    TEXT,
    /** 캔버스 노트 — 앱이 소유한 문서(JSON)를 그대로 저장한다 */
    NOTE
}
