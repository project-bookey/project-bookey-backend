package app.bookey.api.appconfig;

import app.bookey.admin.dto.AdminCsDtos.AdminReasonRequest;
import app.bookey.api.appconfig.dto.AppConfigDtos.*;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.user.DevicePlatform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin AppConfig", description = "앱 버전 · 점검 안내")
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class AppConfigAdminController {

    private final AppConfigService appConfigService;

    @Operation(summary = "플랫폼별 앱 버전 안내")
    @GetMapping("/app-config")
    public List<AppReleaseConfigView> releases() {
        return appConfigService.releases();
    }

    @Operation(summary = "앱 버전 안내 변경 (SUPER_ADMIN) — 최소 지원 버전을 올리면 그보다 낮은 앱은 업데이트 전까지 막힌다")
    @PutMapping("/app-config/{platform}")
    public AppReleaseConfigView updateRelease(@AuthenticationPrincipal AuthAdmin admin,
                                              @PathVariable DevicePlatform platform,
                                              @Valid @RequestBody AppReleaseConfigRequest request) {
        return appConfigService.updateRelease(admin, platform, request);
    }

    @Operation(summary = "점검 일정 — 최근 시작 순")
    @GetMapping("/maintenance-windows")
    public PageResponse<MaintenanceWindowRow> maintenanceWindows(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return appConfigService.maintenanceWindows(page, size);
    }

    @Operation(summary = "점검 예고 (SUPER_ADMIN)")
    @PostMapping("/maintenance-windows")
    public MaintenanceWindowRow createMaintenance(@AuthenticationPrincipal AuthAdmin admin,
                                                  @Valid @RequestBody MaintenanceWindowRequest request) {
        return appConfigService.createMaintenance(admin, request);
    }

    @Operation(summary = "점검 일정 수정 (SUPER_ADMIN) — 끝났거나 취소한 점검은 고칠 수 없다")
    @PutMapping("/maintenance-windows/{id}")
    public MaintenanceWindowRow updateMaintenance(@AuthenticationPrincipal AuthAdmin admin,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody MaintenanceWindowRequest request) {
        return appConfigService.updateMaintenance(admin, id, request);
    }

    @Operation(summary = "점검 취소 (SUPER_ADMIN)")
    @PostMapping("/maintenance-windows/{id}/cancel")
    public MaintenanceWindowRow cancelMaintenance(@AuthenticationPrincipal AuthAdmin admin,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody AdminReasonRequest request) {
        return appConfigService.cancelMaintenance(admin, id, request.reason());
    }
}
