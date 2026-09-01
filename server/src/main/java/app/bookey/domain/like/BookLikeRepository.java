package app.bookey.domain.like;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookLikeRepository extends JpaRepository<BookLike, Long> {
    Optional<BookLike> findByUserIdAndBookId(Long userId, Long bookId);
    long countByBookId(Long bookId);
    boolean existsByUserIdAndBookId(Long userId, Long bookId);
}
