package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClubPostRevealRepository extends JpaRepository<ClubPostReveal, Long> {

    boolean existsByClubPostIdAndUserId(Long clubPostId, Long userId);

    List<ClubPostReveal> findAllByUserIdAndClubPostIdIn(Long userId, List<Long> clubPostIds);
}
