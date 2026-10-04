package app.bookey.domain.remark;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.reading.ReadingStatus;

/** 한 마디를 남긴 때 — 다 읽었을 때(완독)와 내려놓았을 때(하차) 둘뿐이다. */
public enum RemarkKind {
    FINISHED,
    ABANDONED;

    /** 기록의 지금 상태로 정한다 — 아직 읽는 중이거나 담아만 둔 책에는 남길 수 없다. */
    public static RemarkKind of(ReadingStatus status) {
        return switch (status) {
            case FINISHED -> FINISHED;
            case ABANDONED -> ABANDONED;
            default -> throw ApiException.of(ErrorCode.REMARK_NOT_CLOSED);
        };
    }
}
