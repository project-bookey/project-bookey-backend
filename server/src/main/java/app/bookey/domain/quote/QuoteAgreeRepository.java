package app.bookey.domain.quote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuoteAgreeRepository extends JpaRepository<QuoteAgree, Long> {

    Optional<QuoteAgree> findByUserIdAndQuoteId(Long userId, Long quoteId);

    long countByQuoteId(Long quoteId);

    List<QuoteAgree> findAllByUserIdAndQuoteIdIn(Long userId, Collection<Long> quoteIds);

    /** 문장별 나도 그럼 수 — 목록 배치 로딩용 GROUP BY 프로젝션. */
    @Query("""
            SELECT a.quoteId AS quoteId, COUNT(a) AS agreeCount
            FROM QuoteAgree a
            WHERE a.quoteId IN :quoteIds
            GROUP BY a.quoteId
            """)
    List<AgreeCount> countPerQuote(@Param("quoteIds") Collection<Long> quoteIds);

    interface AgreeCount {
        Long getQuoteId();
        long getAgreeCount();
    }
}
