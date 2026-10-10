package app.bookey.api.appconfig.dto;

import app.bookey.domain.user.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AppConfigDtos {

    private AppConfigDtos() {}

    /**
     * 앱 시작 시 읽는 설정.
     *  - updateRequired: 이 버전은 더 쓸 수 없다 — 스토어로 보내고 넘어가지 못하게 한다.
     *  - updateRecommended: 새 버전이 있다 — 권하기만 한다.
     *  - maintenance: 진행 중이거나 다가오는 점검(없으면 null). active 면 점검 화면을 띄운다.
     */
    public record AppConfigView(
            boolean updateRequired,
            boolean updateRecommended,
            @NotNull String minSupportedVersion,
            @NotNull String latestVersion,
            String storeUrl,
            String updateMessage,
            MaintenanceView maintenance
    ) {}

    public record MaintenanceView(
            @NotNull Long id,
            @NotNull String title,
            @NotNull String message,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            boolean active
    ) {}

    // ── 관리자 ──────────────────────────────────────────────

    public record AppReleaseConfigView(
            @NotNull DevicePlatform platform,
            @NotNull String minSupportedVersion,
            @NotNull String latestVersion,
            String storeUrl,
            String updateMessage,
            Long updatedBy,
            String updatedByName,
            @NotNull Instant updatedAt
    ) {}

    public record AppReleaseConfigRequest(
            @NotBlank @Size(max = 20) String minSupportedVersion,
            @NotBlank @Size(max = 20) String latestVersion,
            @Size(max = 500) String storeUrl,
            @Size(max = 300) String updateMessage,
            @NotBlank @Size(max = 500) String reason
    ) {}

    /** status — SCHEDULED(예정) · ACTIVE(진행 중) · ENDED(끝남) · CANCELLED(취소). */
    public record MaintenanceWindowRow(
            @NotNull Long id,
            @NotNull String title,
            @NotNull String message,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            Instant cancelledAt,
            @NotNull Long createdBy,
            @NotNull Instant createdAt,
            @NotNull String status
    ) {}

    public record MaintenanceWindowRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 500) String message,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 500) String reason
    ) {}
}
