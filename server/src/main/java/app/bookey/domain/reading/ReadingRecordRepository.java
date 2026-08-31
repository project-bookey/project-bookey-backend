package app.bookey.domain.reading;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReadingRecordRepository extends JpaRepository<ReadingRecord, Long> {

    Optional<ReadingRecord> findByUserIdAndBookIdAndRound(Long userId, Long bookId, short round);

    List<ReadingRecord> findAllByUserIdAndBookIdOrderByRoundDesc(Long userId, Long bookId);

    List<ReadingRecord> findAllByIdIn(Collection<Long> ids);

    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.userId = :userId
              AND (:status IS NULL OR r.status = :status)
            ORDER BY COALESCE(r.lastReadAt, r.createdAt) DESC
            """)
    Page<ReadingRecord> findLibrary(@Param("userId") Long userId,
                                    @Param("status") ReadingStatus status,
                                    Pageable pageable);

    List<ReadingRecord> findAllByUserIdAndStatus(Long userId, ReadingStatus status);

    long countByUserIdAndStatus(Long userId, ReadingStatus status);

    /** 재촉 후보 조회 — 매일 새벽 배치(§F5). */
    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.status = 'READING'
              AND (r.lastReadAt IS NULL OR r.lastReadAt < :threshold)
            """)
    List<ReadingRecord> findLagCandidates(@Param("threshold") Instant threshold, Pageable pageable);

    /** 24시간 내 완독 수 — 대량 등록 후 일괄 완독 탐지(§8.3). */
    @Query("""
            SELECT COUNT(r) FROM ReadingRecord r
            WHERE r.userId = :userId AND r.finishedAt >= :since
            """)
    long countFinishedSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** 관리자 대시보드 — 전체 사용자 기준 기간 내 완독 수. */
    @Query("SELECT COUNT(r) FROM ReadingRecord r WHERE r.finishedAt >= :since")
    long countAllFinishedSince(@Param("since") Instant since);
}
