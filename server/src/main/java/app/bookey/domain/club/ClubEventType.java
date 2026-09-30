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
    // 폐기됨(모임 노트북) — 옛 모임 이벤트 행 호환용
    NOTE_PAGE_ADDED,
    ENDED
}
