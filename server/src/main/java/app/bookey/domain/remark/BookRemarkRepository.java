package app.bookey.domain.remark;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRemarkRepository extends JpaRepository<BookRemark, Long> {

    /** 도서 상세에서 돌릴 한 마디 — 최근에 쓴 것부터. */
    List<BookRemark> findAllByBookIdOrderByWrittenAtDescIdDesc(Long bookId, Pageable pageable);

    Optional<BookRemark> findByReadingRecordId(Long readingRecordId);
}
