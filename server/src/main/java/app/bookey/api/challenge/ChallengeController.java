package app.bookey.api.challenge;

import app.bookey.api.challenge.dto.ChallengeDtos.ChallengeProgressRequest;
import app.bookey.api.challenge.dto.ChallengeDtos.ChallengeView;
import app.bookey.api.challenge.dto.ChallengeDtos.CreateChallengeRequest;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Challenge", description = "챌린지 — 독서시간 예산 타임워치")
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @Operation(summary = "챌린지 생성 — 즉시 시작")
    @PostMapping
    public ChallengeView create(@AuthenticationPrincipal AuthUser user,
                                @Valid @RequestBody CreateChallengeRequest request) {
        return challengeService.create(user.id(), request);
    }

    @Operation(summary = "진행 중 챌린지 목록")
    @GetMapping("/active")
    public List<ChallengeView> active(@AuthenticationPrincipal AuthUser user) {
        return challengeService.active(user.id());
    }

    @Operation(summary = "챌린지 단건")
    @GetMapping("/{id}")
    public ChallengeView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return challengeService.get(user.id(), id);
    }

    @Operation(summary = "타임워치 재개")
    @PostMapping("/{id}/start")
    public ChallengeView start(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return challengeService.resume(user.id(), id);
    }

    @Operation(summary = "타임워치 일시정지")
    @PostMapping("/{id}/pause")
    public ChallengeView pause(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return challengeService.pause(user.id(), id);
    }

    @Operation(summary = "쪽수 기록 — 총쪽수 도달 시 성공 전이")
    @PatchMapping("/{id}/progress")
    public ChallengeView progress(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody ChallengeProgressRequest request) {
        return challengeService.progress(user.id(), id, request);
    }

    @Operation(summary = "챌린지 포기")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        challengeService.cancel(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
