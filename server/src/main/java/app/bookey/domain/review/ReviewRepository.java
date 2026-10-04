package app.bookey.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

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

    /**
     * 검증 평점 — 완독 검증 리뷰만으로 계산(§F6).
     * 한 사람이 리뷰를 여러 개 쓸 수 있으므로(V46) 사람마다 가장 최근 것 하나만 센다 — 여러 번 써서 평균을 끌지 못하게.
     */
    @Query("""
            SELECT AVG(CAST(r.rating AS double)), COUNT(r)
            FROM Review r
            WHERE r.bookId = :bookId AND r.status = 'VISIBLE'
              AND r.rating IS NOT NULL AND r.verificationLevel = 'VERIFIED_FULL'
              AND NOT EXISTS (
                  SELECT n.id FROM Review n
                  WHERE n.userId = r.userId AND n.bookId = r.bookId AND n.status = 'VISIBLE'
                    AND n.rating IS NOT NULL AND n.verificationLevel = 'VERIFIED_FULL'
                    AND n.id > r.id)
            """)
    List<Object[]> verifiedRating(@Param("bookId") Long bookId);

    /** 전체 평점 — 검증 평점과 같이 사람마다 별점을 준 가장 최근 리뷰 하나만 센다. */
    @Query("""
            SELECT AVG(CAST(r.rating AS double)), COUNT(r)
            FROM Review r
            WHERE r.bookId = :bookId AND r.status = 'VISIBLE' AND r.rating IS NOT NULL
              AND NOT EXISTS (
                  SELECT n.id FROM Review n
                  WHERE n.userId = r.userId AND n.bookId = r.bookId AND n.status = 'VISIBLE'
                    AND n.rating IS NOT NULL
                    AND n.id > r.id)
            """)
    List<Object[]> overallRating(@Param("bookId") Long bookId);

    Page<Review> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);

    long countByStatus(String status);

    long countByVerificationLevelInAndStatus(List<VerificationLevel> levels, String status);
}
