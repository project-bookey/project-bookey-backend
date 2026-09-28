package app.bookey.domain.club;

public enum ClubEventType {
    CREATED,
    JOINED,
    LEFT,
    KICKED,
    FINISHED,
    CHECKPOINT_MET,
    CHECKPOINT_MISSED,
    POSTED,
    /** 노트북에 새 페이지가 생김 — payload {pageId, seq} */
    NOTE_PAGE_ADDED,
    ENDED
}
