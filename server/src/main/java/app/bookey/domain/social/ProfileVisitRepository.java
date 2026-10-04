package app.bookey.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ProfileVisitRepository extends JpaRepository<ProfileVisit, Long> {

    boolean existsByVisitorIdAndHostIdAndVisitDate(Long visitorId, Long hostId, LocalDate visitDate);

    long countByHostId(Long hostId);

    Page<ProfileVisit> findAllByHostIdOrderByIdDesc(Long hostId, Pageable pageable);

    /** 탈퇴 — 내가 다녀간 기록과 나를 찾은 기록을 함께 지운다. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "DELETE FROM ProfileVisit v WHERE v.visitorId = :userId OR v.hostId = :userId")
    int deleteAllInvolving(@org.springframework.data.repository.query.Param("userId") Long userId);
}
