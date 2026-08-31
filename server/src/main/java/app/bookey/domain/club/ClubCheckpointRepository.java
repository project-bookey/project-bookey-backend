package app.bookey.domain.club;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClubCheckpointRepository extends JpaRepository<ClubCheckpoint, Long> {

    List<ClubCheckpoint> findAllByClubBookIdOrderBySeqAsc(Long clubBookId);

    void deleteAllByClubBookId(Long clubBookId);

    /** 마감됐는데 아직 평가되지 않은 체크포인트 — 배치 대상. */
    @Query("SELECT c FROM ClubCheckpoint c WHERE c.evaluatedAt IS NULL AND c.dueAt < :now")
    List<ClubCheckpoint> findDue(@Param("now") Instant now);

    /** 마감 24h 이내로 다가온 체크포인트 — 임박 알림용. */
    @Query("""
            SELECT c FROM ClubCheckpoint c
            WHERE c.evaluatedAt IS NULL AND c.dueAt BETWEEN :from AND :to
            """)
    List<ClubCheckpoint> findUpcoming(@Param("from") Instant from, @Param("to") Instant to);

    Optional<ClubCheckpoint> findFirstByClubBookIdAndEvaluatedAtIsNullOrderBySeqAsc(Long clubBookId);
}
