package app.bookey.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByUserIdAndPostId(Long userId, Long postId);

    long countByPostId(Long postId);

    List<PostLike> findAllByUserIdAndPostIdIn(Long userId, Collection<Long> postIds);

    /** 독후감별 좋아요 수 — 목록 배치 로딩용 GROUP BY 프로젝션(QuoteAgreeRepository.countPerQuote 미러). */
    @Query("""
            SELECT l.postId AS postId, COUNT(l) AS likeCount
            FROM PostLike l
            WHERE l.postId IN :postIds
            GROUP BY l.postId
            """)
    List<PostLikeCount> countPerPost(@Param("postIds") Collection<Long> postIds);

    interface PostLikeCount {
        Long getPostId();
        long getLikeCount();
    }
}
