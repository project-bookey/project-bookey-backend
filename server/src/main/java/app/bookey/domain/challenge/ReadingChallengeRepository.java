package app.bookey.domain.challenge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReadingChallengeRepository extends JpaRepository<ReadingChallenge, Long> {
    List<ReadingChallenge> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ChallengeStatus status);
    Optional<ReadingChallenge> findByIdAndUserId(Long id, Long userId);
    boolean existsByReadingRecordIdAndStatus(Long readingRecordId, ChallengeStatus status);
}
