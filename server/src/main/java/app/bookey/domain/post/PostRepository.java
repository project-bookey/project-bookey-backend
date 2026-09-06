package app.bookey.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByUserIdAndSlug(Long userId, String slug);

    boolean existsByUserIdAndSlug(Long userId, String slug);

    Page<Post> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Post> findAllByUserIdAndVisibilityOrderByPublishedAtDescIdDesc(Long userId,
                                                                        PostVisibility visibility,
                                                                        Pageable pageable);

    Page<Post> findAllByBookIdAndVisibilityOrderByPublishedAtDescIdDesc(Long bookId,
                                                                        PostVisibility visibility,
                                                                        Pageable pageable);

    /** 광장 독후감 피드 — 공개 독후감만 최신순, 같은 시각이면 id 로 안정 정렬. */
    @Query("""
            SELECT p FROM Post p
            WHERE p.visibility = 'PUBLIC'
            ORDER BY p.publishedAt DESC, p.id DESC
            """)
    Page<Post> findFeed(Pageable pageable);

    /**
     * 알고리즘 피드 (§14.1 초기판) — (좋아요+1) / (경과시간+2)^1.5 점수 내림차순.
     * 새 글은 위로 올라오고, 좋아요가 붙을수록 오래 머문다. 작성자 다양성·개인화는 후속.
     */
    @Query(value = """
            SELECT p.* FROM posts p
            WHERE p.visibility = 'PUBLIC'
            ORDER BY (COALESCE((SELECT COUNT(*) FROM post_likes l WHERE l.post_id = p.id), 0) + 1)
                     / POWER(GREATEST(EXTRACT(EPOCH FROM (now() - p.published_at)) / 3600, 0) + 2, 1.5) DESC,
                     p.id DESC
            """,
            countQuery = "SELECT COUNT(*) FROM posts p WHERE p.visibility = 'PUBLIC'",
            nativeQuery = true)
    Page<Post> findHotFeed(Pageable pageable);

    long countByUserIdAndVisibility(Long userId, PostVisibility visibility);
}
