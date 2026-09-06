package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ProfileVisitRepository extends JpaRepository<ProfileVisit, Long> {

    boolean existsByVisitorIdAndHostIdAndVisitDate(Long visitorId, Long hostId, LocalDate visitDate);

    long countByHostId(Long hostId);

    Page<ProfileVisit> findAllByHostIdOrderByIdDesc(Long hostId, Pageable pageable);
}
