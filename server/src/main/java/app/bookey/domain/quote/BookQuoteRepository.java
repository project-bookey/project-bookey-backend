package app.bookey.domain.quote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookQuoteRepository extends JpaRepository<BookQuote, Long> {

    Page<BookQuote> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<BookQuote> findAllByBookIdOrderByCreatedAtDescIdDesc(Long bookId, Pageable pageable);

    /** 독후감에 인용할 밑줄 고르기 — 내가 그 책에서 오려둔 문장만. */
    Page<BookQuote> findAllByUserIdAndBookIdOrderByCreatedAtDescIdDesc(Long userId, Long bookId, Pageable pageable);

    /** 광장(플라자) 밑줄 피드용 — B2에서 사용한다. */
    Page<BookQuote> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    // ────────────────────────── 검색어가 있을 때 쓰는 짝 ──────────────────────────
    // 위 파생 쿼리 넷과 범위가 하나씩 짝을 이룬다. 문장 내용이나 책 제목에 검색어가 들어가면 걸린다(대소문자 무시,
    // 부분 일치). 책 제목은 BookQuote 에 연관이 없어 bookId 서브쿼리로 거른다. 정렬은 짝이 되는 파생 쿼리와 같다.

    /** 내가 오려둔 문장 검색. */
    @Query("""
            SELECT q FROM BookQuote q
            WHERE q.userId = :userId
              AND (LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR q.bookId IN (SELECT b.id FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))))
            ORDER BY q.createdAt DESC, q.id DESC
            """)
    Page<BookQuote> searchByUserId(@Param("userId") Long userId,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    /** 내가 그 책에서 오려둔 문장 검색. */
    @Query("""
            SELECT q FROM BookQuote q
            WHERE q.userId = :userId AND q.bookId = :bookId
              AND (LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR q.bookId IN (SELECT b.id FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))))
            ORDER BY q.createdAt DESC, q.id DESC
            """)
    Page<BookQuote> searchByUserIdAndBookId(@Param("userId") Long userId,
                                            @Param("bookId") Long bookId,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    /** 그 책에 달린 모두의 문장 검색. */
    @Query("""
            SELECT q FROM BookQuote q
            WHERE q.bookId = :bookId
              AND (LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR q.bookId IN (SELECT b.id FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))))
            ORDER BY q.createdAt DESC, q.id DESC
            """)
    Page<BookQuote> searchByBookId(@Param("bookId") Long bookId,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    /** 광장(플라자) 밑줄 피드 검색 — 전체 대상. */
    @Query("""
            SELECT q FROM BookQuote q
            WHERE LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR q.bookId IN (SELECT b.id FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY q.createdAt DESC, q.id DESC
            """)
    Page<BookQuote> searchAll(@Param("keyword") String keyword, Pageable pageable);
}
