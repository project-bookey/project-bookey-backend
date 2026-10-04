package app.bookey.domain.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookLikeRepository extends JpaRepository<BookLike, Long> {
    Optional<BookLike> findByUserIdAndBookId(Long userId, Long bookId);

    /** 책 좋아요 수 — 탈퇴한(계정이 종료된) 사람의 좋아요는 빼고 센다. */
    @Query("""
            SELECT COUNT(l) FROM BookLike l
            WHERE l.bookId = :bookId
              AND l.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            """)
    long countByBookId(@Param("bookId") Long bookId);

    boolean existsByUserIdAndBookId(Long userId, Long bookId);
}
