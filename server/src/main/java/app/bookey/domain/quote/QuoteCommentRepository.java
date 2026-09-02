package app.bookey.domain.quote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuoteCommentRepository extends JpaRepository<QuoteComment, Long> {

    /** 밑줄 하나의 댓글 — 오래된 순(대화 흐름). */
    Page<QuoteComment> findAllByQuoteIdOrderByCreatedAtAscIdAsc(Long quoteId, Pageable pageable);

    long countByQuoteId(Long quoteId);

    /** 문장별 댓글 수 — 목록 배치 로딩용 GROUP BY 프로젝션(QuoteAgreeRepository.countPerQuote 미러). */
    @Query("""
            SELECT c.quoteId AS quoteId, COUNT(c) AS commentCount
            FROM QuoteComment c
            WHERE c.quoteId IN :quoteIds
            GROUP BY c.quoteId
            """)
    List<CommentCount> countPerQuote(@Param("quoteIds") Collection<Long> quoteIds);

    interface CommentCount {
        Long getQuoteId();
        long getCommentCount();
    }
}
