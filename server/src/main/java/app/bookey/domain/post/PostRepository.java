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
}
