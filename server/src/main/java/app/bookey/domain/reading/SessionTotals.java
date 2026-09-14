package app.bookey.domain.reading;

/**
 * 세션 합산 — 읽은 쪽 · 시간(초) · 읽은 사람 수.
 * JPQL 생성자 표현식의 SUM·COUNT 는 Long 으로 오므로 래퍼 타입으로 받고, null 은 0 으로 본다.
 */
public record SessionTotals(Long pagesRead, Long durationSec, Long readerCount) {

    public SessionTotals {
        pagesRead = pagesRead == null ? 0L : pagesRead;
        durationSec = durationSec == null ? 0L : durationSec;
        readerCount = readerCount == null ? 0L : readerCount;
    }

    public static SessionTotals empty() {
        return new SessionTotals(0L, 0L, 0L);
    }
}
