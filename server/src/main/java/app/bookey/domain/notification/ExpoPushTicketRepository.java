package app.bookey.domain.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ExpoPushTicketRepository extends JpaRepository<ExpoPushTicket, Long> {
    @Query("select t from ExpoPushTicket t where t.status = 'PENDING' and t.nextCheckAt <= :now order by t.id")
    List<ExpoPushTicket> findDue(Instant now, Pageable pageable);
}
