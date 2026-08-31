package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClubCheckpointProgressRepository extends JpaRepository<ClubCheckpointProgress, Long> {

    List<ClubCheckpointProgress> findAllByCheckpointId(Long checkpointId);

    List<ClubCheckpointProgress> findAllByCheckpointIdIn(List<Long> checkpointIds);

    List<ClubCheckpointProgress> findAllByClubMemberId(Long clubMemberId);

    @Query("""
            SELECT COUNT(p) FROM ClubCheckpointProgress p
            WHERE p.checkpointId = :checkpointId AND p.achieved = true
            """)
    long countAchieved(@Param("checkpointId") Long checkpointId);
}
