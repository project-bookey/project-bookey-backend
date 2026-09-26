package app.bookey.domain.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyAttendanceRepository extends JpaRepository<DailyAttendance, Long> {
    Optional<DailyAttendance> findByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);
    Optional<DailyAttendance> findTopByUserIdOrderByAttendanceDateDesc(Long userId);
    long countByUserIdAndAttendanceDateBetween(Long userId, LocalDate from, LocalDate to);
}
