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

    Optional<ReadingRecord> findFirstByUserIdAndBookIdOrderByRoundDesc(Long userId, Long bookId);

    List<ReadingRecord> findAllByIdIn(Collection<Long> ids);

    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.userId = :userId
            ORDER BY COALESCE(r.lastReadAt, r.createdAt) DESC
            """)
    Page<ReadingRecord> findLibrary(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.userId = :userId AND r.status = :status
            ORDER BY COALESCE(r.lastReadAt, r.createdAt) DESC
            """)
    Page<ReadingRecord> findLibraryByStatus(@Param("userId") Long userId,
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

    /** 책별 서재 담김 수 — 같은 사용자의 회차 반복은 1로 센다. */
    @Query("""
            SELECT r.bookId AS bookId, COUNT(DISTINCT r.userId) AS savedCount
            FROM ReadingRecord r
            GROUP BY r.bookId
            ORDER BY COUNT(DISTINCT r.userId) DESC, r.bookId ASC
            """)
    List<BookSavedCount> countSavedPerBook(Pageable pageable);

    /** 광장(플라자) 완독 자랑 피드용 — idx_reading_records_finished_feed 부분 인덱스를 탄다. */
    @Query("""
            SELECT r FROM ReadingRecord r
            WHERE r.status = 'FINISHED' AND r.finishedAt IS NOT NULL
            ORDER BY r.finishedAt DESC, r.id DESC
            """)
    Page<ReadingRecord> findFinishFeed(Pageable pageable);

    interface BookSavedCount {
        Long getBookId();
        long getSavedCount();
    }
}
