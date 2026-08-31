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

    /** 내부 캐시 조회 (§F1 파이프라인 1번). */
    @Query("""
            SELECT b FROM Book b
            WHERE LOWER(b.title)  LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<Book> searchCache(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT b FROM Book b
            WHERE (:keyword IS NULL
                   OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR b.isbn13 = :keyword)
            """)
    Page<Book> searchForAdmin(@Param("keyword") String keyword, Pageable pageable);
}
