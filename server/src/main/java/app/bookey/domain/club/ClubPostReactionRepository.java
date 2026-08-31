package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubPostReactionRepository extends JpaRepository<ClubPostReaction, Long> {

    Optional<ClubPostReaction> findByClubPostIdAndUserIdAndKind(Long clubPostId, Long userId,
                                                                ReactionKind kind);

    List<ClubPostReaction> findAllByClubPostIdInAndUserId(List<Long> clubPostIds, Long userId);

    List<ClubPostReaction> findAllByClubPostId(Long clubPostId);
}
