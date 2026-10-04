package app.bookey.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByUserIdAndPostId(Long userId, Long postId);

    /** 좋아요 누른 사람 목록 — 최신순 (§14.2 구독 열람권). 탈퇴한 사람은 빼고 센다(아래 수도 같다). */
    @Query("""
            SELECT l FROM PostLike l
            WHERE l.postId = :postId
              AND l.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY l.id DESC
            """)
    org.springframework.data.domain.Page<PostLike> findAllByPostIdOrderByIdDesc(
            @Param("postId") Long postId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT COUNT(l) FROM PostLike l
            WHERE l.postId = :postId
              AND l.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            """)
    long countByPostId(@Param("postId") Long postId);

    List<PostLike> findAllByUserIdAndPostIdIn(Long userId, Collection<Long> postIds);

    /** 독후감별 좋아요 수 — 목록 배치 로딩용 GROUP BY 프로젝션. */
    @Query("""
            SELECT l.postId AS postId, COUNT(l) AS likeCount
            FROM PostLike l
            WHERE l.postId IN :postIds
              AND l.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            GROUP BY l.postId
            """)
    List<PostLikeCount> countPerPost(@Param("postIds") Collection<Long> postIds);

    interface PostLikeCount {
        Long getPostId();
        long getLikeCount();
    }
}
