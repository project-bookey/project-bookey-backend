package app.bookey.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByUserIdAndSlug(Long userId, String slug);

    boolean existsByUserIdAndSlug(Long userId, String slug);

    Page<Post> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Post> findAllByUserIdAndVisibilityOrderByPublishedAtDesc(Long userId,
                                                                  PostVisibility visibility,
                                                                  Pageable pageable);

    Page<Post> findAllByBookIdAndVisibilityOrderByPublishedAtDesc(Long bookId,
                                                                  PostVisibility visibility,
                                                                  Pageable pageable);
}
