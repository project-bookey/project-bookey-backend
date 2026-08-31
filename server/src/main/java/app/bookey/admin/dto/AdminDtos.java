package app.bookey.admin.dto;

import app.bookey.domain.admin.*;
import app.bookey.domain.club.ClubStatus;
import app.bookey.domain.review.VerificationLevel;
import app.bookey.domain.user.UserStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AdminDtos {

    private AdminDtos() {}

    // ── 인증 ─────────────────────────────────────────────────
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            /** 2FA 가 켜진 계정은 필수. */
            String totpCode
    ) {}

    public record LoginResponse(
            String accessToken,
            long expiresInSec,
            boolean totpRequired,
            AdminProfile admin
    ) {}

    public record AdminProfile(@NotNull Long id, @NotNull String email, @NotNull String name, @NotNull AdminRole role,
                               boolean totpEnabled, Instant lastLoginAt) {}

    public record CreateAdminRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 12, max = 100) String password,
            @NotBlank @Size(max = 50) String name,
            @NotNull AdminRole role
    ) {}

    // ── 대시보드 ─────────────────────────────────────────────
    public record DashboardView(
            long totalUsers,
            long activeUsersToday,
            long readingSessionsToday,
            long finishedBooksToday,
            double verifiedReviewRatio,
            long pendingModeration,
            long overdueModeration,
            long activeClubs,
            double notificationConversionRate7d
    ) {}

    // ── 회원 ────────────────────────────────────────────────
    public record UserRow(
            @NotNull Long id,
            @NotNull String handle,
            @NotNull String nickname,
            String maskedEmail,
            @NotNull UserStatus status,
            @NotNull Instant createdAt,
            long booksReading,
            long booksFinished
    ) {}

    public record UserDetailView(
            @NotNull Long id,
            @NotNull String handle,
            @NotNull String nickname,
            String email,          // 마스킹 여부는 revealEmail 파라미터로 결정
            @NotNull UserStatus status,
            @NotNull Instant createdAt,
            long totalSessions,
            long totalDurationSec,
            long reviewCount,
            long clubCount,
            List<SanctionRow> sanctions
    ) {}

    public record SanctionRow(@NotNull Long id, @NotNull SanctionType type, @NotNull String reason, @NotNull Instant startsAt,
                              Instant endsAt, Instant releasedAt, Long adminId) {}

    public record SanctionRequest(
            @NotNull SanctionType type,
            @NotBlank @Size(max = 500) String reason,
            /** null 이면 영구. */
            Integer durationDays
    ) {}

    // ── 도서 ────────────────────────────────────────────────
    public record BookRow(@NotNull Long id, String isbn13, @NotNull String title, String author, String publisher,
                          Integer totalPages, @NotNull String source, boolean userCreated, @NotNull Instant createdAt) {}

    public record UpdateBookRequest(
            @Size(max = 500) String title,
            @Size(max = 500) String author,
            @Size(max = 255) String publisher,
            @Min(1) @Max(20000) Integer totalPages,
            String coverUrl,
            @Size(max = 255) String category,
            @NotBlank @Size(max = 500) String reason
    ) {}

    // ── 신고 큐 ─────────────────────────────────────────────
    public record ModerationRow(
            @NotNull Long id,
            @NotNull ModerationSource sourceType,
            @NotNull Long sourceId,
            @NotNull String reason,
            int reportCount,
            short priority,
            @NotNull Instant slaDueAt,
            boolean overdue,
            @NotNull ModerationStatus status,
            Long assignedAdminId,
            String contentPreview,
            Long authorId,
            String authorNickname
    ) {}

    public record ResolveRequest(
            @NotNull ModerationResolution resolution,
            @Size(max = 500) String note,
            /** SANCTION 선택 시 함께 적용할 제재. */
            SanctionRequest sanction
    ) {}

    // ── 검증 심사 ───────────────────────────────────────────
    public record ReviewRow(
            @NotNull Long id,
            @NotNull Long bookId,
            String bookTitle,
            @NotNull Long authorId,
            String authorNickname,
            Short rating,
            @NotNull String body,
            @NotNull VerificationLevel verificationLevel,
            Map<String, Object> verificationSnapshot,
            int reportCount,
            @NotNull String status,
            @NotNull Instant createdAt
    ) {}

    public record OverrideVerificationRequest(
            @NotNull VerificationLevel level,
            @NotBlank @Size(max = 500) String reason
    ) {}

    // ── 모임 ────────────────────────────────────────────────
    public record ClubRow(
            @NotNull Long id,
            @NotNull String name,
            @NotNull String joinCode,
            @NotNull ClubStatus status,
            int memberCount,
            int memberLimit,
            @NotNull LocalDate startsAt,
            @NotNull LocalDate endsAt,
            @NotNull Long ownerId,
            String ownerNickname,
            long postCount,
            @NotNull Instant createdAt
    ) {}

    public record ClubActionRequest(@NotBlank @Size(max = 500) String reason) {}

    // ── 알림 운영 ───────────────────────────────────────────
    public record NotificationStats(
            long sent7d,
            long converted7d,
            double conversionRate,
            boolean pushEnabled
    ) {}

    public record OpsFlagRequest(@NotNull Boolean enabled, @Size(max = 300) String note) {}

    public record OpsFlagRow(@NotNull String key, boolean enabled, String note, @NotNull Instant updatedAt) {}

    // ── 감사 로그 ───────────────────────────────────────────
    public record AuditRow(@NotNull Long id, @NotNull Long adminId, @NotNull String action, String targetType, Long targetId,
                           String reason, String ip, @NotNull Instant createdAt) {}
}
