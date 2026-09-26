package app.bookey.api.attendance;

import app.bookey.api.attendance.AttendanceDtos.AttendanceView;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Attendance", description = "일일 출석체크")
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "오늘 출석 상태")
    @GetMapping
    public AttendanceView status(@AuthenticationPrincipal AuthUser user) {
        return attendanceService.status(user.id());
    }

    @Operation(summary = "오늘 출석체크 — KST 기준 하루 한 번 보상")
    @PostMapping
    public AttendanceView checkIn(@AuthenticationPrincipal AuthUser user) {
        return attendanceService.checkIn(user.id());
    }
}
