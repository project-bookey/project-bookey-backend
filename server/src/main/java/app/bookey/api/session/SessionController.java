package app.bookey.api.session;

import app.bookey.api.session.dto.SessionDtos.*;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Session", description = "독서 세션 — 타이머 · 수동 기록")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "타이머 시작")
    @PostMapping("/start")
    public SessionView start(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody StartRequest request) {
        return sessionService.start(user.id(), request);
    }

    @Operation(summary = "진행 중인 세션 조회 — 앱 재시작 시 복원")
    @GetMapping("/current")
    public SessionView current(@AuthenticationPrincipal AuthUser user) {
        return sessionService.current(user.id());
    }

    @Operation(summary = "타이머 종료 — 읽은 쪽수 입력")
    @PostMapping("/{sessionId}/end")
    public SessionEndResult end(@AuthenticationPrincipal AuthUser user,
                                @PathVariable Long sessionId,
                                @Valid @RequestBody EndRequest request) {
        return sessionService.end(user.id(), sessionId, request);
    }

    @Operation(summary = "수동 기록 — 사후 입력 (검증 가중치 낮음)")
    @PostMapping("/manual")
    public SessionEndResult manual(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody ManualRequest request) {
        return sessionService.recordManual(user.id(), request);
    }

    @Operation(summary = "도서별 세션 목록")
    @GetMapping
    public List<SessionView> list(@AuthenticationPrincipal AuthUser user,
                                  @RequestParam(required = false) Long readingRecordId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "30") int size) {
        if (readingRecordId != null) {
            return sessionService.listByRecord(user.id(), readingRecordId);
        }
        return sessionService.listRecent(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "세션 삭제")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long sessionId) {
        sessionService.deleteSession(user.id(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
