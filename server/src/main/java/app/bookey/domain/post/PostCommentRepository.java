package app.bookey.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /** 루트 댓글 — 오래된 순(대화 흐름), 같은 시각이면 id 로 안정 정렬. */
    Page<PostComment> findAllByPostIdAndParentIdIsNullOrderByCreatedAtAscIdAsc(Long postId, Pageable pageable);

    /** 루트 댓글들에 달린 답글 — 배치 로딩. */
    List<PostComment> findAllByParentIdInOrderByCreatedAtAscIdAsc(Collection<Long> parentIds);

    /** 독후감별 댓글 수(답글 포함) — 목록 배치 로딩용 GROUP BY 프로젝션. */
    @Query("""
            SELECT c.postId AS postId, COUNT(c) AS commentCount
            FROM PostComment c
            WHERE c.postId IN :postIds
            GROUP BY c.postId
            """)
    List<PostCommentCount> countPerPost(@Param("postIds") Collection<Long> postIds);

    long countByPostId(Long postId);

    interface PostCommentCount {
        Long getPostId();
        long getCommentCount();
    }
}
