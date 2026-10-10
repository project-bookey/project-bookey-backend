package app.bookey.domain.remark;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRemarkRepository extends JpaRepository<BookRemark, Long>,
        JpaSpecificationExecutor<BookRemark> {

    /** 도서 상세에서 돌릴 한 마디 — 최근에 쓴 것부터. 탈퇴한(계정이 종료된) 사람의 것은 뺀다. */
    @Query("""
            SELECT r FROM BookRemark r
            WHERE r.bookId = :bookId
              AND r.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY r.writtenAt DESC, r.id DESC
            """)
    List<BookRemark> findAllByBookIdOrderByWrittenAtDescIdDesc(@Param("bookId") Long bookId, Pageable pageable);

    Optional<BookRemark> findByReadingRecordId(Long readingRecordId);

    /** 광장 완독 자랑에 붙일 한 마디 — 회차마다 하나라 reading_record_id 유니크 인덱스를 탄다. */
    List<BookRemark> findAllByReadingRecordIdIn(Collection<Long> readingRecordIds);
}
