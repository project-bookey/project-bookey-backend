package app.bookey.domain.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ModerationTicketRepository extends JpaRepository<ModerationTicket, Long> {

    Optional<ModerationTicket> findBySourceTypeAndSourceId(ModerationSource sourceType, Long sourceId);

    @Query("""
            SELECT t FROM ModerationTicket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:sourceType IS NULL OR t.sourceType = :sourceType)
            ORDER BY t.priority ASC, t.slaDueAt ASC
            """)
    Page<ModerationTicket> search(@Param("status") ModerationStatus status,
                                  @Param("sourceType") ModerationSource sourceType,
                                  Pageable pageable);

    long countByStatus(ModerationStatus status);

    @Query("SELECT COUNT(t) FROM ModerationTicket t WHERE t.status <> 'RESOLVED' AND t.slaDueAt < :now")
    long countOverdue(@Param("now") Instant now);
}
