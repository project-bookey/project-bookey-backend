package app.bookey.domain.remark;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRemarkRepository extends JpaRepository<BookRemark, Long> {

    /** 도서 상세에서 돌릴 한 마디 — 최근에 쓴 것부터. */
    List<BookRemark> findAllByBookIdOrderByWrittenAtDescIdDesc(Long bookId, Pageable pageable);

    Optional<BookRemark> findByReadingRecordId(Long readingRecordId);

    /** 광장 완독 자랑에 붙일 한 마디 — 회차마다 하나라 reading_record_id 유니크 인덱스를 탄다. */
    List<BookRemark> findAllByReadingRecordIdIn(Collection<Long> readingRecordIds);
}
