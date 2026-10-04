package app.bookey.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /**
     * 루트 댓글 — 오래된 순(대화 흐름), 같은 시각이면 id 로 안정 정렬.
     * 탈퇴한 사람의 댓글은 뺀다 — 거기 달린 답글도 함께 보이지 않는다(30일 뒤 계정이 지워질 때 함께 지워질 답글이다).
     */
    @Query("""
            SELECT c FROM PostComment c
            WHERE c.postId = :postId AND c.parentId IS NULL
              AND c.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    Page<PostComment> findAllByPostIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(@Param("postId") Long postId,
                                                                               Pageable pageable);

    /** 루트 댓글들에 달린 답글 — 배치 로딩. 탈퇴한 사람의 답글은 뺀다. */
    @Query("""
            SELECT c FROM PostComment c
            WHERE c.parentId IN :parentIds
              AND c.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<PostComment> findAllByParentIdInOrderByCreatedAtAscIdAsc(@Param("parentIds") Collection<Long> parentIds);

    /** 독후감별 댓글 수(답글 포함) — 목록 배치 로딩용 GROUP BY 프로젝션. 보이는 댓글만 센다. */
    @Query("""
            SELECT c.postId AS postId, COUNT(c) AS commentCount
            FROM PostComment c
            WHERE c.postId IN :postIds
              AND c.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
              AND (c.parentId IS NULL OR c.parentId NOT IN (
                  SELECT r.id FROM PostComment r
                  WHERE r.userId IN (SELECT rt.id FROM User rt WHERE rt.status = 'TERMINATED')))
            GROUP BY c.postId
            """)
    List<PostCommentCount> countPerPost(@Param("postIds") Collection<Long> postIds);

    interface PostCommentCount {
        Long getPostId();
        long getCommentCount();
    }
}
