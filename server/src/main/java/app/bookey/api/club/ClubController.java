package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Club", description = "독서 모임 — 코드 참가 · 진척 공유 · 체크포인트")
@RestController
@RequestMapping("/api/v1/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;
    private final ClubNudgeService nudgeService;

    @Operation(summary = "모임 만들기 — 초대 코드 자동 발급")
    @PostMapping
    public ClubHomeView create(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody CreateClubRequest request) {
        return clubService.create(user.id(), request);
    }

    @Operation(summary = "내 모임 목록")
    @GetMapping
    public PageResponse<ClubSummaryView> myClubs(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return clubService.myClubs(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "공개 모임 둘러보기")
    @GetMapping("/public")
    public PageResponse<ClubPreview> publicClubs(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return clubService.publicClubs(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "초대 코드로 모임 미리보기 — 참가 전 확인용")
    @GetMapping("/preview")
    public ClubPreview preview(@AuthenticationPrincipal AuthUser user,
                               @RequestParam String code,
                               HttpServletRequest servletRequest) {
        String clientKey = user != null ? "u" + user.id() : "ip" + servletRequest.getRemoteAddr();
        return clubService.preview(user == null ? null : user.id(), code, clientKey);
    }

    @Operation(summary = "코드로 참가 — 도서 자동 등록 + 진척 공유 동의")
    @PostMapping("/join")
    public ClubHomeView join(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody JoinRequest request) {
        return clubService.join(user.id(), request);
    }

    @Operation(summary = "모임 홈 — 멤버 진척 · 체크포인트 그리드")
    @GetMapping("/{clubId}")
    public ClubHomeView home(@AuthenticationPrincipal AuthUser user, @PathVariable Long clubId) {
        return clubService.home(user.id(), clubId);
    }

    @Operation(summary = "모임 정보 수정 (호스트)")
    @PatchMapping("/{clubId}")
    public ClubHomeView update(@AuthenticationPrincipal AuthUser user,
                               @PathVariable Long clubId,
                               @Valid @RequestBody UpdateClubRequest request) {
        return clubService.update(user.id(), clubId, request);
    }

    @Operation(summary = "초대 코드 회전 (호스트) — 유출 시 즉시 무효화")
    @PostMapping("/{clubId}/rotate-code")
    public Map<String, String> rotateCode(@AuthenticationPrincipal AuthUser user,
                                          @PathVariable Long clubId) {
        return Map.of("joinCode", clubService.rotateJoinCode(user.id(), clubId));
    }

    @Operation(summary = "내 공유 설정 변경 — 진척 공개 · 찌르기 수신")
    @PatchMapping("/{clubId}/sharing")
    public ResponseEntity<Void> updateSharing(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long clubId,
                                              @RequestBody UpdateSharingRequest request) {
        clubService.updateSharing(user.id(), clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모임 나가기")
    @DeleteMapping("/{clubId}/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long clubId) {
        clubService.leave(user.id(), clubId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "멤버 강퇴 (호스트) — 사유 필수")
    @PostMapping("/{clubId}/kick")
    public ResponseEntity<Void> kick(@AuthenticationPrincipal AuthUser user,
                                     @PathVariable Long clubId,
                                     @Valid @RequestBody KickRequest request) {
        clubService.kick(user.id(), clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "호스트 권한 넘기기")
    @PostMapping("/{clubId}/transfer-host")
    public ResponseEntity<Void> transferHost(@AuthenticationPrincipal AuthUser user,
                                             @PathVariable Long clubId,
                                             @Valid @RequestBody TransferHostRequest request) {
        clubService.transferHost(user.id(), clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모임 종료 (호스트)")
    @PostMapping("/{clubId}/end")
    public ResponseEntity<Void> end(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long clubId) {
        clubService.end(user.id(), clubId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모임 결산 — 완독률 · 총 시간 · 베스트 인용")
    @GetMapping("/{clubId}/result")
    public ClubResultView result(@AuthenticationPrincipal AuthUser user, @PathVariable Long clubId) {
        return clubService.result(user.id(), clubId);
    }

    @Operation(summary = "찌르기 — 프리셋 문구만, 대상당 24h 1회 · 하루 3회")
    @PostMapping("/{clubId}/nudges")
    public Map<String, Integer> nudge(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long clubId,
                                      @Valid @RequestBody NudgeRequest request) {
        nudgeService.nudge(user.id(), clubId, request);
        return Map.of("remainingToday", nudgeService.remainingToday(user.id()));
    }
}
