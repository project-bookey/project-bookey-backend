package app.bookey.api.club;

import app.bookey.api.club.ClubLogService.CreateLogCommand;
import app.bookey.api.club.dto.ClubDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.domain.club.SpoilerLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Club Log", description = "모임 읽기로그 — 세션 끝 조각 · 하루 보드 · 지금 읽는 중")
@RestController
@RequestMapping("/api/v1/clubs/{clubId}")
@RequiredArgsConstructor
public class ClubLogController {

    private final ClubLogService logService;

    @Operation(summary = "조각 남기기 — 사진 한 장(선택) + 한 줄, 쪽에 붙이면 그 쪽까지 읽은 멤버에게만 보인다")
    @PostMapping(value = "/logs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClubPostView create(@AuthenticationPrincipal AuthUser user,
                               @PathVariable Long clubId,
                               @RequestPart(value = "file", required = false) MultipartFile file,
                               @RequestParam(required = false) String body,
                               @RequestParam(required = false) Integer anchorPage,
                               @RequestParam(required = false) SpoilerLevel spoilerLevel,
                               @RequestParam(required = false) Long readingSessionId) {
        return logService.create(user.id(), clubId,
                new CreateLogCommand(body, anchorPage, spoilerLevel, readingSessionId), file);
    }

    @Operation(summary = "읽기로그 하루 보드 — 그날 조각 + 모임 합산(date 는 KST, 비우면 오늘)")
    @GetMapping("/logs")
    public ClubLogDayView day(@AuthenticationPrincipal AuthUser user,
                              @PathVariable Long clubId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return logService.day(user.id(), clubId, date);
    }

    @Operation(summary = "요일 스트립 — 날짜마다 조각 수(최대 14일)")
    @GetMapping("/logs/days")
    public List<ClubLogDayCount> days(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long clubId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return logService.days(user.id(), clubId, from, to);
    }

    @Operation(summary = "지금 읽는 중 — 열린 독서 세션이 있는 멤버(진척 비공개·나 제외)")
    @GetMapping("/reading-now")
    public List<ReadingNowView> readingNow(@AuthenticationPrincipal AuthUser user, @PathVariable Long clubId) {
        return logService.readingNow(user.id(), clubId);
    }
}
