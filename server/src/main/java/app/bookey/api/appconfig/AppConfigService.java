package app.bookey.api.appconfig;

import app.bookey.admin.support.AdminAuditService;
import app.bookey.api.appconfig.dto.AppConfigDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.appconfig.AppReleaseConfig;
import app.bookey.domain.appconfig.AppReleaseConfigRepository;
import app.bookey.domain.appconfig.AppVersion;
import app.bookey.domain.appconfig.MaintenanceWindow;
import app.bookey.domain.appconfig.MaintenanceWindowRepository;
import app.bookey.domain.user.DevicePlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 앱 버전 안내와 점검 일정. 앱은 공개 API 로 읽기만 하고, 바꾸는 것은 최고 관리자만 한다(되돌리기 어려운 강제 업데이트).
 * 서버는 점검 중에도 요청을 막지 않는다 — 앱이 안내를 보고 스스로 점검 화면을 띄운다.
 */
@Service
@RequiredArgsConstructor
public class AppConfigService {

    private final AppReleaseConfigRepository releaseRepository;
    private final MaintenanceWindowRepository maintenanceRepository;
    private final AdminRepository adminRepository;
    private final AdminAuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AppConfigView publicConfig(DevicePlatform platform, String appVersion) {
        AppReleaseConfig config = releaseRepository.findById(platform)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Instant now = clock.instant();
        MaintenanceView maintenance = maintenanceRepository
                .findAllByCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAsc(now).stream()
                .findFirst()
                .map(w -> new MaintenanceView(w.getId(), w.getTitle(), w.getMessage(), w.getStartsAt(), w.getEndsAt(),
                        w.isActiveAt(now)))
                .orElse(null);
        return new AppConfigView(config.requiresUpdate(appVersion), config.recommendsUpdate(appVersion),
                config.getMinSupportedVersion(), config.getLatestVersion(), config.getStoreUrl(),
                config.getUpdateMessage(), maintenance);
    }

    // ── 앱 버전 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AppReleaseConfigView> releases() {
        List<AppReleaseConfig> configs = releaseRepository.findAll().stream()
                .sorted(Comparator.comparing(AppReleaseConfig::getPlatform))
                .toList();
        Map<Long, String> names = adminNames(configs.stream().map(AppReleaseConfig::getUpdatedBy).toList());
        return configs.stream().map(c -> toView(c, names)).toList();
    }

    @Transactional
    public AppReleaseConfigView updateRelease(AuthAdmin admin, DevicePlatform platform, AppReleaseConfigRequest req) {
        requireOps(admin);
        String min = req.minSupportedVersion().trim();
        String latest = req.latestVersion().trim();
        if (!AppVersion.isValid(min) || !AppVersion.isValid(latest)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "버전은 1.2.3 처럼 숫자와 점으로 적어 주세요.");
        }
        if (AppVersion.compare(min, latest) > 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "최소 지원 버전이 최신 버전보다 높을 수 없습니다.");
        }
        String storeUrl = blankToNull(req.storeUrl());
        if (storeUrl != null && !storeUrl.startsWith("https://")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "스토어 주소는 https:// 로 시작해야 합니다.");
        }
        AppReleaseConfig config = releaseRepository.findById(platform)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Map<String, Object> before = snapshot(config);
        config.update(min, latest, storeUrl, blankToNull(req.updateMessage()), admin.id(), clock.instant());
        auditService.log(admin, "UPDATE_APP_RELEASE", "APP_RELEASE", null, req.reason(), before, snapshot(config));
        return toView(config, adminNames(List.of(admin.id())));
    }

    // ── 점검 ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceWindowRow> maintenanceWindows(int page, int size) {
        Instant now = clock.instant();
        return PageResponse.of(maintenanceRepository.findAllByOrderByStartsAtDesc(
                        PageRequest.of(page, Math.clamp(size, 1, 100))),
                w -> toRow(w, now));
    }

    @Transactional
    public MaintenanceWindowRow createMaintenance(AuthAdmin admin, MaintenanceWindowRequest req) {
        requireOps(admin);
        Instant now = clock.instant();
        validatePeriod(req, now);
        MaintenanceWindow window = maintenanceRepository.save(new MaintenanceWindow(
                req.title().trim(), req.message().trim(), req.startsAt(), req.endsAt(), admin.id()));
        auditService.log(admin, "CREATE_MAINTENANCE", "MAINTENANCE", window.getId(), req.reason(), null,
                windowSnapshot(window));
        return toRow(window, now);
    }

    @Transactional
    public MaintenanceWindowRow updateMaintenance(AuthAdmin admin, Long id, MaintenanceWindowRequest req) {
        requireOps(admin);
        Instant now = clock.instant();
        MaintenanceWindow window = maintenanceRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!window.isUpcomingOrActiveAt(now)) {
            throw new ApiException(ErrorCode.CONFLICT, "끝났거나 취소한 점검은 고칠 수 없습니다.");
        }
        validatePeriod(req, now);
        Map<String, Object> before = windowSnapshot(window);
        window.update(req.title().trim(), req.message().trim(), req.startsAt(), req.endsAt());
        auditService.log(admin, "UPDATE_MAINTENANCE", "MAINTENANCE", id, req.reason(), before, windowSnapshot(window));
        return toRow(window, now);
    }

    @Transactional
    public MaintenanceWindowRow cancelMaintenance(AuthAdmin admin, Long id, String reason) {
        requireOps(admin);
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        Instant now = clock.instant();
        MaintenanceWindow window = maintenanceRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!window.isUpcomingOrActiveAt(now)) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 끝났거나 취소한 점검입니다.");
        }
        window.cancel(now);
        auditService.log(admin, "CANCEL_MAINTENANCE", "MAINTENANCE", id, reason, windowSnapshot(window), null);
        return toRow(window, now);
    }

    // ── 내부 ────────────────────────────────────────────────

    private static void validatePeriod(MaintenanceWindowRequest req, Instant now) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "끝나는 시각이 시작 시각보다 뒤여야 합니다.");
        }
        if (!req.endsAt().isAfter(now)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이미 지난 기간입니다.");
        }
    }

    private static MaintenanceWindowRow toRow(MaintenanceWindow w, Instant now) {
        String status = w.isCancelled() ? "CANCELLED"
                : w.isActiveAt(now) ? "ACTIVE"
                : now.isBefore(w.getStartsAt()) ? "SCHEDULED"
                : "ENDED";
        return new MaintenanceWindowRow(w.getId(), w.getTitle(), w.getMessage(), w.getStartsAt(), w.getEndsAt(),
                w.getCancelledAt(), w.getCreatedBy(), w.getCreatedAt(), status);
    }

    private static AppReleaseConfigView toView(AppReleaseConfig c, Map<Long, String> names) {
        return new AppReleaseConfigView(c.getPlatform(), c.getMinSupportedVersion(), c.getLatestVersion(),
                c.getStoreUrl(), c.getUpdateMessage(), c.getUpdatedBy(),
                c.getUpdatedBy() == null ? null : names.get(c.getUpdatedBy()), c.getUpdatedAt());
    }

    private Map<Long, String> adminNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return adminRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Admin::getId, Admin::getName, (a, b) -> a));
    }

    private static Map<String, Object> snapshot(AppReleaseConfig c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("platform", c.getPlatform().name());
        map.put("minSupportedVersion", c.getMinSupportedVersion());
        map.put("latestVersion", c.getLatestVersion());
        map.put("storeUrl", c.getStoreUrl());
        map.put("updateMessage", c.getUpdateMessage());
        return map;
    }

    private static Map<String, Object> windowSnapshot(MaintenanceWindow w) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", w.getTitle());
        map.put("startsAt", w.getStartsAt().toString());
        map.put("endsAt", w.getEndsAt().toString());
        map.put("cancelledAt", w.getCancelledAt() == null ? null : w.getCancelledAt().toString());
        return map;
    }

    private static void requireOps(AuthAdmin admin) {
        if (!admin.role().canManageOps()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
