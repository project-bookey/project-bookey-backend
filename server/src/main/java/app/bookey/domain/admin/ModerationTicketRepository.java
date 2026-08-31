package app.bookey.domain.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 신고 큐. 정렬은 우선순위 → SLA 마감 순으로 Pageable 에 담아 전달한다. */
public interface ModerationTicketRepository extends JpaRepository<ModerationTicket, Long> {

    Optional<ModerationTicket> findBySourceTypeAndSourceId(ModerationSource sourceType, Long sourceId);

    Page<ModerationTicket> findAllBy(Pageable pageable);

    Page<ModerationTicket> findAllByStatus(ModerationStatus status, Pageable pageable);

    Page<ModerationTicket> findAllBySourceType(ModerationSource sourceType, Pageable pageable);

    Page<ModerationTicket> findAllByStatusAndSourceType(ModerationStatus status,
                                                        ModerationSource sourceType,
                                                        Pageable pageable);

    long countByStatus(ModerationStatus status);

    @Query("SELECT COUNT(t) FROM ModerationTicket t WHERE t.status <> 'RESOLVED' AND t.slaDueAt < :now")
    long countOverdue(@Param("now") Instant now);
}
