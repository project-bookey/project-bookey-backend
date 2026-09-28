package app.bookey.domain.club;

import java.time.Instant;

/**
 * 노트북 목록용 페이지 요약 — JPQL 생성자 프로젝션. 문서(jsonb, 최대 512KB)는 읽지 않으려고 따로 둔다.
 * 생성자 표현식의 숫자 컬럼은 래퍼로 받는다(SessionTotals 와 같은 이유).
 */
public record ClubNotePageSummary(Long id, Integer seq, String title, Integer version, Integer elementCount,
                                  Long updatedBy, Instant updatedAt) {
}
