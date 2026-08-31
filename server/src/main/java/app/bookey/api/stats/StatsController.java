package app.bookey.api.stats;

import app.bookey.api.session.dto.SessionDtos.StatsSummary;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Stats", description = "독서 통계 · 스트릭 · 히트맵")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "기간 통계 — 캘린더 히트맵용 일별 데이터 포함")
    @GetMapping
    public StatsSummary summary(@AuthenticationPrincipal AuthUser user,
                                @RequestParam(defaultValue = "90") int days) {
        return statsService.summary(user.id(), days);
    }
}
