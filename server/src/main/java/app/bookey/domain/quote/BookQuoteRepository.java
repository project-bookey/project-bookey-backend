package app.bookey.domain.quote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookQuoteRepository extends JpaRepository<BookQuote, Long> {

    Page<BookQuote> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<BookQuote> findAllByBookIdOrderByCreatedAtDescIdDesc(Long bookId, Pageable pageable);

    /** 독후감에 인용할 밑줄 고르기 — 내가 그 책에서 오려둔 문장만. */
    Page<BookQuote> findAllByUserIdAndBookIdOrderByCreatedAtDescIdDesc(Long userId, Long bookId, Pageable pageable);

    /** 광장(플라자) 밑줄 피드용 — B2에서 사용한다. */
    Page<BookQuote> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
