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

    public record AdminProfile(Long id, String email, String name, AdminRole role,
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
            Long id,
            String handle,
            String nickname,
            String maskedEmail,
            UserStatus status,
            Instant createdAt,
            long booksReading,
            long booksFinished
    ) {}

    public record UserDetailView(
            Long id,
            String handle,
            String nickname,
            String email,          // 마스킹 여부는 revealEmail 파라미터로 결정
            UserStatus status,
            Instant createdAt,
            long totalSessions,
            long totalDurationSec,
            long reviewCount,
            long clubCount,
            List<SanctionRow> sanctions
    ) {}

    public record SanctionRow(Long id, SanctionType type, String reason, Instant startsAt,
                              Instant endsAt, Instant releasedAt, Long adminId) {}

    public record SanctionRequest(
            @NotNull SanctionType type,
            @NotBlank @Size(max = 500) String reason,
            /** null 이면 영구. */
            Integer durationDays
    ) {}

    // ── 도서 ────────────────────────────────────────────────
    public record BookRow(Long id, String isbn13, String title, String author, String publisher,
                          Integer totalPages, String source, boolean userCreated, Instant createdAt) {}

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
            Long id,
            ModerationSource sourceType,
            Long sourceId,
            String reason,
            int reportCount,
            short priority,
            Instant slaDueAt,
            boolean overdue,
            ModerationStatus status,
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
            Long id,
            Long bookId,
            String bookTitle,
            Long authorId,
            String authorNickname,
            Short rating,
            String body,
            VerificationLevel verificationLevel,
            Map<String, Object> verificationSnapshot,
            int reportCount,
            String status,
            Instant createdAt
    ) {}

    public record OverrideVerificationRequest(
            @NotNull VerificationLevel level,
            @NotBlank @Size(max = 500) String reason
    ) {}

    // ── 모임 ────────────────────────────────────────────────
    public record ClubRow(
            Long id,
            String name,
            String joinCode,
            ClubStatus status,
            int memberCount,
            int memberLimit,
            LocalDate startsAt,
            LocalDate endsAt,
            Long ownerId,
            String ownerNickname,
            long postCount,
            Instant createdAt
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

    public record OpsFlagRow(String key, boolean enabled, String note, Instant updatedAt) {}

    // ── 감사 로그 ───────────────────────────────────────────
    public record AuditRow(Long id, Long adminId, String action, String targetType, Long targetId,
                           String reason, String ip, Instant createdAt) {}
}
