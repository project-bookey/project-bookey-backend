package app.bookey.domain.appconfig;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, Long> {

    /** 아직 끝나지 않은 점검(취소 제외) — 시작 순. 앱 안내와 관리자 화면에서 쓴다. */
    List<MaintenanceWindow> findAllByCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(Instant now);

    Page<MaintenanceWindow> findAllByOrderByStartsAtDesc(Pageable pageable);
}
