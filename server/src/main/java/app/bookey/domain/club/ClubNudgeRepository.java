package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ClubNudgeRepository extends JpaRepository<ClubNudge, Long> {

    /** 같은 대상에게 24h 1회 제한 (§12.4). */
    boolean existsByFromUserIdAndToUserIdAndCreatedAtAfter(Long fromUserId, Long toUserId, Instant after);

    /** 하루 총 3회 제한. */
    long countByFromUserIdAndCreatedAtAfter(Long fromUserId, Instant after);
}
