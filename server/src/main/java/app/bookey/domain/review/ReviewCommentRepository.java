package app.bookey.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    /** 최상위 댓글만 — 오래된 순(대화 흐름). 답글은 접어두고 별도 엔드포인트로 편다. */
    Page<ReviewComment> findAllByReviewIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(Long reviewId, Pageable pageable);

    /** 한 댓글의 답글 — 오래된 순. */
    Page<ReviewComment> findAllByParentIdOrderByCreatedAtAscIdAsc(Long parentId, Pageable pageable);

    /**
     * 리뷰별 댓글 수 — 목록 배치 로딩용 GROUP BY 프로젝션(QuoteCommentRepository.countPerQuote 미러).
     * 답글도 함께 센다 — ReviewView 의 commentCount 는 "이 리뷰에 달린 말 전체 수"다.
     */
    @Query("""
            SELECT c.reviewId AS reviewId, COUNT(c) AS commentCount
            FROM ReviewComment c
            WHERE c.reviewId IN :reviewIds
            GROUP BY c.reviewId
            """)
    List<CommentCount> countPerReview(@Param("reviewIds") Collection<Long> reviewIds);

    /** 부모별 답글 수 — 목록 배치 로딩용 GROUP BY 프로젝션(countPerReview 미러). */
    @Query("""
            SELECT c.parentId AS parentId, COUNT(c) AS replyCount
            FROM ReviewComment c
            WHERE c.parentId IN :parentIds
            GROUP BY c.parentId
            """)
    List<ReplyCount> countPerParent(@Param("parentIds") Collection<Long> parentIds);

    interface CommentCount {
        Long getReviewId();
        long getCommentCount();
    }

    interface ReplyCount {
        Long getParentId();
        long getReplyCount();
    }
}
