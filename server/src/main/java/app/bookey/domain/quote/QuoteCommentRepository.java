package app.bookey.domain.quote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuoteCommentRepository extends JpaRepository<QuoteComment, Long> {

    /** 최상위 댓글만 — 오래된 순(대화 흐름). 답글은 접어두고 별도 엔드포인트로 편다. */
    Page<QuoteComment> findAllByQuoteIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(Long quoteId, Pageable pageable);

    /** 한 댓글의 답글 — 오래된 순. */
    Page<QuoteComment> findAllByParentIdOrderByCreatedAtAscIdAsc(Long parentId, Pageable pageable);

    /**
     * 문장별 댓글 수 — 목록 배치 로딩용 GROUP BY 프로젝션(QuoteAgreeRepository.countPerQuote 미러).
     * 답글도 함께 센다 — BookQuoteView·PlazaItemView 의 commentCount 는 "이 밑줄에 달린 말 전체 수"다.
     */
    @Query("""
            SELECT c.quoteId AS quoteId, COUNT(c) AS commentCount
            FROM QuoteComment c
            WHERE c.quoteId IN :quoteIds
            GROUP BY c.quoteId
            """)
    List<CommentCount> countPerQuote(@Param("quoteIds") Collection<Long> quoteIds);

    /** 부모별 답글 수 — 목록 배치 로딩용 GROUP BY 프로젝션(countPerQuote 미러). */
    @Query("""
            SELECT c.parentId AS parentId, COUNT(c) AS replyCount
            FROM QuoteComment c
            WHERE c.parentId IN :parentIds
            GROUP BY c.parentId
            """)
    List<ReplyCount> countPerParent(@Param("parentIds") Collection<Long> parentIds);

    interface CommentCount {
        Long getQuoteId();
        long getCommentCount();
    }

    interface ReplyCount {
        Long getParentId();
        long getReplyCount();
    }
}
