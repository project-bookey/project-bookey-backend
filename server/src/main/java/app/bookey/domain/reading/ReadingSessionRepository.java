package app.bookey.domain.reading;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    Optional<ReadingSession> findByUserIdAndEndedAtIsNull(Long userId);

    Optional<ReadingSession> findByUserIdAndClientUuid(Long userId, UUID clientUuid);

    List<ReadingSession> findAllByReadingRecordIdOrderByStartedAtDesc(Long readingRecordId);

    Page<ReadingSession> findAllByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    List<ReadingSession> findAllByUserIdAndStartedAtBetweenOrderByStartedAtAsc(
            Long userId, Instant from, Instant to);

    /** 최근 N일 읽은 페이지 합 — 실제 일일 페이스 계산(§F4). */
    @Query("""
            SELECT COALESCE(SUM(s.endPage - s.startPage), 0)
            FROM ReadingSession s
            WHERE s.readingRecordId = :recordId
              AND s.startedAt >= :since
              AND s.endPage IS NOT NULL AND s.startPage IS NOT NULL
              AND s.endPage > s.startPage
            """)
    long sumPagesSince(@Param("recordId") Long recordId, @Param("since") Instant since);

    @Query("""
            SELECT COALESCE(SUM(s.durationSec), 0)
            FROM ReadingSession s
            WHERE s.readingRecordId = :recordId
            """)
    long sumDurationSec(@Param("recordId") Long recordId);

    @Query("""
            SELECT COALESCE(SUM(s.durationSec), 0)
            FROM ReadingSession s
            WHERE s.userId = :userId AND s.startedAt >= :since
            """)
    long sumDurationSecByUserSince(@Param("userId") Long userId, @Param("since") Instant since);

    long countByReadingRecordIdAndSource(Long readingRecordId, SessionSource source);

    /** 검증 등급 산정용 — 커버리지 계산은 서비스에서 구간 병합으로 처리(§8.2 ①). */
    @Query("""
            SELECT s FROM ReadingSession s
            WHERE s.readingRecordId = :recordId
              AND s.startPage IS NOT NULL AND s.endPage IS NOT NULL
              AND s.endPage > s.startPage
            ORDER BY s.startPage ASC
            """)
    List<ReadingSession> findPageRanges(@Param("recordId") Long recordId);

    /** 자동 종료 대상 — 4시간 초과 열린 세션. */
    @Query("SELECT s FROM ReadingSession s WHERE s.endedAt IS NULL AND s.startedAt < :threshold")
    List<ReadingSession> findStaleOpenSessions(@Param("threshold") Instant threshold);

    @Query("""
            SELECT COUNT(DISTINCT CAST(s.startedAt AS date))
            FROM ReadingSession s
            WHERE s.userId = :userId AND s.startedAt >= :since
            """)
    long countDistinctReadingDaysSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** 관리자 대시보드 — 기간 내 세션을 기록한 고유 사용자 수. */
    @Query("SELECT COUNT(DISTINCT s.userId) FROM ReadingSession s WHERE s.startedAt >= :since")
    long countActiveUsersSince(@Param("since") Instant since);

    @Query("SELECT COUNT(s) FROM ReadingSession s WHERE s.startedAt >= :since")
    long countSessionsSince(@Param("since") Instant since);

    long countByUserId(Long userId);
}
