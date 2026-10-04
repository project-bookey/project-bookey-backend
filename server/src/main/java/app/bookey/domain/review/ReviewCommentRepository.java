package app.bookey.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    /**
     * 최상위 댓글만 — 오래된 순(대화 흐름). 답글은 접어두고 별도 엔드포인트로 편다.
     * 탈퇴한(계정이 종료된) 사람의 댓글은 뺀다 — 거기 달린 답글도 함께 보이지 않는다.
     */
    @Query("""
            SELECT c FROM ReviewComment c
            WHERE c.reviewId = :reviewId AND c.parentId IS NULL
              AND c.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    Page<ReviewComment> findAllByReviewIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(@Param("reviewId") Long reviewId,
                                                                                   Pageable pageable);

    /** 한 댓글의 답글 — 오래된 순. 탈퇴한 사람의 답글은 뺀다. */
    @Query("""
            SELECT c FROM ReviewComment c
            WHERE c.parentId = :parentId
              AND c.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    Page<ReviewComment> findAllByParentIdOrderByCreatedAtAscIdAsc(@Param("parentId") Long parentId, Pageable pageable);

    /**
     * 리뷰별 댓글 수 — 목록 배치 로딩용 GROUP BY 프로젝션.
     * 답글도 함께 센다 — ReviewView 의 commentCount 는 "이 리뷰에 달린 말 전체 수"다.
     * 보이는 것만 센다 — 탈퇴한 사람의 댓글과 그 아래 답글은 빠진다.
     */
    @Query("""
            SELECT c.reviewId AS reviewId, COUNT(c) AS commentCount
            FROM ReviewComment c
            WHERE c.reviewId IN :reviewIds
              AND c.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
              AND (c.parentId IS NULL OR c.parentId NOT IN (
                  SELECT r.id FROM ReviewComment r
                  WHERE r.userId IN (SELECT rt.id FROM User rt WHERE rt.status = 'TERMINATED')))
            GROUP BY c.reviewId
            """)
    List<CommentCount> countPerReview(@Param("reviewIds") Collection<Long> reviewIds);

    /** 부모별 답글 수 — 목록 배치 로딩용 GROUP BY 프로젝션(countPerReview 미러). 탈퇴한 사람의 답글은 뺀다. */
    @Query("""
            SELECT c.parentId AS parentId, COUNT(c) AS replyCount
            FROM ReviewComment c
            WHERE c.parentId IN :parentIds
              AND c.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
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
