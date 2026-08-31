package app.bookey.api.notification;

import app.bookey.api.notification.dto.NotificationDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "알림 목록 · 설정")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록")
    @GetMapping
    public PageResponse<NotificationView> list(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "알림 열람 처리")
    @PostMapping("/{notificationId}/open")
    public ResponseEntity<Void> open(@AuthenticationPrincipal AuthUser user,
                                     @PathVariable Long notificationId) {
        notificationService.markOpened(user.id(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "알림 설정 변경 — 톤 · 조용시간 · 빈도")
    @PatchMapping("/settings")
    public ResponseEntity<Void> updateSettings(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody NotificationSettingsRequest request) {
        notificationService.updateSettings(user.id(), request);
        return ResponseEntity.noContent().build();
    }
}
