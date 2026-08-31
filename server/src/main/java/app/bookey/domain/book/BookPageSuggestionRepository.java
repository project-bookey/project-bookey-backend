package app.bookey.domain.book;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookPageSuggestionRepository extends JpaRepository<BookPageSuggestion, Long> {

    Optional<BookPageSuggestion> findByBookIdAndUserId(Long bookId, Long userId);

    /** 가장 많이 제안된 페이지 수와 표 수 (다수결 채택용). */
    @Query("""
            SELECT s.totalPages AS pages, COUNT(s) AS votes
            FROM BookPageSuggestion s
            WHERE s.bookId = :bookId
            GROUP BY s.totalPages
            ORDER BY COUNT(s) DESC
            """)
    List<Object[]> tallyVotes(@Param("bookId") Long bookId);
}
