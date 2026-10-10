package app.bookey.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 남에게 보여 주는 조회는 모두 status = 'VISIBLE' 만 고른다 — 신고·관리자 조치로 숨긴(HIDDEN) 글과 지운(DELETED) 글은
 * 피드·책·모임·프로필·공개 블로그에 나오지 않는다. 내 글 목록만 숨긴 글을 함께 보여 준다(지운 글은 빼고).
 * 새 조회를 만들면 이 조건을 빠뜨리지 말 것.
 */
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    Optional<Post> findByUserIdAndSlug(Long userId, String slug);

    boolean existsByUserIdAndSlug(Long userId, String slug);

    /** 내 독후감 — 숨긴 글은 작성자에게 보이므로 함께, 관리자가 지운 글은 뺀다. */
    @Query("""
            SELECT p FROM Post p
            WHERE p.userId = :userId AND p.status <> 'DELETED'
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    /** 한 사람의 공개 범위별 글 — 남에게 보이는 글(VISIBLE)만. */
    @Query("""
            SELECT p FROM Post p
            WHERE p.userId = :userId AND p.visibility = :visibility AND p.status = 'VISIBLE'
            ORDER BY p.publishedAt DESC, p.id DESC
            """)
    Page<Post> findAllByUserIdAndVisibilityOrderByPublishedAtDescIdDesc(@Param("userId") Long userId,
                                                                        @Param("visibility") PostVisibility visibility,
                                                                        Pageable pageable);

    /** 책별 독후감 — 탈퇴한(계정이 종료된) 사람의 글은 뺀다. */
    @Query("""
            SELECT p FROM Post p
            WHERE p.bookId = :bookId AND p.visibility = :visibility AND p.status = 'VISIBLE'
              AND p.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY p.publishedAt DESC, p.id DESC
            """)
    Page<Post> findAllByBookIdAndVisibilityOrderByPublishedAtDescIdDesc(@Param("bookId") Long bookId,
                                                                        @Param("visibility") PostVisibility visibility,
                                                                        Pageable pageable);

    /** 광장 독후감 피드 — 공개 독후감만 최신순, 같은 시각이면 id 로 안정 정렬. 탈퇴한 사람의 글은 뺀다. */
    @Query("""
            SELECT p FROM Post p
            WHERE p.visibility = 'PUBLIC' AND p.status = 'VISIBLE'
              AND p.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY p.publishedAt DESC, p.id DESC
            """)
    Page<Post> findFeed(Pageable pageable);

    /**
     * 알고리즘 피드 (§14.1 초기판) — (좋아요+1) / (경과시간+2)^1.5 점수 내림차순.
     * 새 글은 위로 올라오고, 좋아요가 붙을수록 오래 머문다. 작성자 다양성·개인화는 후속. 탈퇴한 사람의 글은 뺀다.
     */
    @Query(value = """
            SELECT p.* FROM posts p
            WHERE p.visibility = 'PUBLIC' AND p.status = 'VISIBLE'
              AND p.user_id NOT IN (SELECT t.id FROM users t WHERE t.status = 'TERMINATED')
            ORDER BY (COALESCE((SELECT COUNT(*) FROM post_likes l WHERE l.post_id = p.id), 0) + 1)
                     / POWER(GREATEST(EXTRACT(EPOCH FROM (now() - p.published_at)) / 3600, 0) + 2, 1.5) DESC,
                     p.id DESC
            """,
            countQuery = """
                    SELECT COUNT(*) FROM posts p
                    WHERE p.visibility = 'PUBLIC' AND p.status = 'VISIBLE'
                      AND p.user_id NOT IN (SELECT t.id FROM users t WHERE t.status = 'TERMINATED')
                    """,
            nativeQuery = true)
    Page<Post> findHotFeed(Pageable pageable);

    /** 모임 독후감 — 모임 글은 PUBLIC·CLUB 만 가질 수 있어 공개 범위로 거르지 않는다. 탈퇴한 사람의 글은 뺀다. */
    @Query("""
            SELECT p FROM Post p
            WHERE p.clubId = :clubId AND p.status = 'VISIBLE'
              AND p.userId NOT IN (SELECT t.id FROM User t WHERE t.status = 'TERMINATED')
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    Page<Post> findAllByClubIdOrderByCreatedAtDescIdDesc(@Param("clubId") Long clubId, Pageable pageable);

    /** 프로필의 공개 글 수 — 남에게 보이는 글만 센다. */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.userId = :userId AND p.visibility = :visibility AND p.status = 'VISIBLE'")
    long countByUserIdAndVisibility(@Param("userId") Long userId, @Param("visibility") PostVisibility visibility);
}
