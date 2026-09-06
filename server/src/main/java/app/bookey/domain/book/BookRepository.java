package app.bookey.domain.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByIsbn13(String isbn13);

    List<Book> findAllByIsbn13In(Collection<String> isbn13s);

    /** 온보딩 책 고르기 — 카테고리 부분 일치(선택), 표지 있는 책 우선 최신순. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT b FROM Book b
            WHERE (:category IS NULL OR b.category LIKE CONCAT('%', CAST(:category AS string), '%'))
            ORDER BY CASE WHEN b.coverUrl IS NULL THEN 1 ELSE 0 END, b.id DESC
            """)
    List<Book> findOnboardingPicks(@org.springframework.data.repository.query.Param("category") String category,
                                   org.springframework.data.domain.Pageable pageable);

    /** 내부 캐시 조회 (§F1 파이프라인 1번). */
    @Query("""
            SELECT b FROM Book b
            WHERE LOWER(b.title)  LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<Book> searchCache(@Param("keyword") String keyword, Pageable pageable);

    /** 관리자 도서 검색. 키워드가 없으면 빈 문자열로 대체해 전체를 반환한다. */
    @Query("""
            SELECT b FROM Book b
            WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
               OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
               OR COALESCE(b.isbn13, '') = COALESCE(:keyword, '')
            """)
    Page<Book> searchForAdmin(@Param("keyword") String keyword, Pageable pageable);
}
