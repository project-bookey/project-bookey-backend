package app.bookey.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndReadingRecordId(Long userId, Long readingRecordId);

    boolean existsByUserIdAndReadingRecordId(Long userId, Long readingRecordId);

    /**
     * 도서 상세 리뷰 목록.
     * 기본 정렬: 완독 검증 > 부분 검증 > 미검증, 동일 등급 내 도움됨 순 (§F6).
     */
    @Query("""
            SELECT r FROM Review r
            WHERE r.bookId = :bookId AND r.status = 'VISIBLE'
              AND (:verifiedOnly = false OR r.verificationLevel = 'VERIFIED_FULL')
            ORDER BY CASE r.verificationLevel
                        WHEN 'VERIFIED_FULL' THEN 0
                        WHEN 'VERIFIED_PARTIAL' THEN 1
                        WHEN 'UNVERIFIED' THEN 2
                        ELSE 3 END ASC,
                     r.helpfulCount DESC, r.createdAt DESC
            """)
    Page<Review> findByBook(@Param("bookId") Long bookId,
                            @Param("verifiedOnly") boolean verifiedOnly,
                            Pageable pageable);

    /** 검증 평점 — 완독 검증 리뷰만으로 계산(§F6). */
    @Query("""
            SELECT AVG(CAST(r.rating AS double)), COUNT(r)
            FROM Review r
            WHERE r.bookId = :bookId AND r.status = 'VISIBLE'
              AND r.rating IS NOT NULL AND r.verificationLevel = 'VERIFIED_FULL'
            """)
    List<Object[]> verifiedRating(@Param("bookId") Long bookId);

    @Query("""
            SELECT AVG(CAST(r.rating AS double)), COUNT(r)
            FROM Review r
            WHERE r.bookId = :bookId AND r.status = 'VISIBLE' AND r.rating IS NOT NULL
            """)
    List<Object[]> overallRating(@Param("bookId") Long bookId);

    Page<Review> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);

    long countByStatus(String status);

    long countByVerificationLevelInAndStatus(List<VerificationLevel> levels, String status);
}
