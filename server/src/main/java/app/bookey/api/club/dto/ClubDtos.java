package app.bookey.api.club.dto;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.domain.club.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ClubDtos {

    private ClubDtos() {}

    // ── 생성 · 수정 ───────────────────────────────────────────
    public record CreateClubRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 1000) String description,
            @NotNull Long bookId,
            @NotNull LocalDate startsAt,
            @NotNull LocalDate endsAt,
            ClubVisibility visibility,
            @Min(2) @Max(50) Integer memberLimit,
            Boolean allowNudge,
            /** 주차별 체크포인트 자동 생성 (총 페이지를 주차 수로 균등 분배). */
            Boolean autoCheckpoints,
            List<CheckpointRequest> checkpoints
    ) {}

    public record CheckpointRequest(
            @Size(max = 60) String title,
            @NotNull @Min(1) Integer targetPage,
            @NotNull Instant dueAt
    ) {}

    public record UpdateClubRequest(
            @Size(max = 60) String name,
            @Size(max = 1000) String description,
            ClubVisibility visibility,
            @Min(2) @Max(50) Short memberLimit,
            LocalDate endsAt,
            Boolean allowNudge
    ) {}

    public record JoinRequest(
            @NotBlank @Size(max = 12) String code,
            /** 모임 목표일을 내 개인 완독 목표일로 삼을지 (§12.1 참가 플로우 ②). */
            Boolean adoptTargetDate,
            /** 진척 공개 동의 (§12.1 ③). false 면 비공개로 참가. */
            Boolean shareProgress
    ) {}

    public record UpdateSharingRequest(Boolean shareProgress, Boolean allowNudge) {}

    public record KickRequest(@NotNull Long userId, @NotBlank @Size(max = 200) String reason) {}

    public record TransferHostRequest(@NotNull Long userId) {}

    // ── 조회 ─────────────────────────────────────────────────

    /** 코드로 볼 수 있는 정보는 미리보기 수준까지만 (§8.5). */
    public record ClubPreview(
            Long id,
            String name,
            String description,
            BookSummary book,
            String hostNickname,
            int memberCount,
            int memberLimit,
            LocalDate startsAt,
            LocalDate endsAt,
            ClubStatus status,
            boolean alreadyMember,
            boolean joinable,
            String joinBlockedReason
    ) {}

    public record ClubSummaryView(
            Long id,
            String name,
            String coverUrl,
            BookSummary book,
            ClubStatus status,
            int memberCount,
            long daysLeft,
            Double myCompletionRate,
            Double averageCompletionRate,
            int unreadPostCount
    ) {}

    public record MemberProgressView(
            Long userId,
            Long clubMemberId,
            String nickname,
            String avatarUrl,
            ClubRole role,
            boolean isMe,
            /** 진척 비공개 멤버는 아래 값들이 모두 null 이다. */
            boolean shareProgress,
            Integer currentPage,
            Double completionRate,
            Long totalDurationSec,
            Instant lastReadAt,
            Boolean finished,
            String paceStatus,      // ON_TRACK | BEHIND | AT_RISK | null(비공개)
            boolean nudgeable
    ) {}

    public record CheckpointView(
            Long id,
            short seq,
            String title,
            int targetPage,
            Instant dueAt,
            boolean evaluated,
            long achievedCount,
            long memberCount,
            Boolean myAchieved
    ) {}

    public record ClubHomeView(
            Long id,
            String name,
            String description,
            String coverUrl,
            String joinCode,          // 호스트/멤버에게만 노출
            ClubVisibility visibility,
            ClubStatus status,
            BookSummary book,
            LocalDate startsAt,
            LocalDate endsAt,
            long daysLeft,
            int memberCount,
            int memberLimit,
            ClubRole myRole,
            boolean myShareProgress,
            boolean myAllowNudge,
            int myRank,
            Double averageCompletionRate,
            List<MemberProgressView> members,
            List<CheckpointView> checkpoints,
            CheckpointView nextCheckpoint
    ) {}

    public record ClubResultView(
            Long clubId,
            String name,
            BookSummary book,
            int memberCount,
            long finishedCount,
            double finishRate,
            long totalDurationSec,
            List<MemberProgressView> members,
            List<String> bestQuotes,
            String topDiscussant
    ) {}

    // ── 토론 ─────────────────────────────────────────────────
    public record CreatePostRequest(
            ClubPostType type,
            @NotBlank @Size(max = 10000) String body,
            @Min(0) Integer anchorPage,
            SpoilerLevel spoilerLevel,
            Long parentId,
            Long linkedPostId
    ) {}

    public record PostView(
            Long id,
            Long parentId,
            ClubPostType type,
            Long authorId,
            String authorNickname,
            String authorAvatarUrl,
            /** 마스킹된 글은 body 가 null 이다 — 서버가 애초에 내려보내지 않는다(§8.5). */
            String body,
            boolean masked,
            Integer anchorPage,
            SpoilerLevel spoilerLevel,
            boolean pinned,
            int commentCount,
            int reactionCount,
            List<String> myReactions,
            Instant createdAt,
            List<PostView> comments
    ) {}

    public record ReactionRequest(@NotNull ReactionKind kind) {}

    public record NudgeRequest(
            @NotNull Long toUserId,
            @NotNull NudgeMessage messageKey
    ) {}

    public record ReportRequest(
            @NotBlank @Size(max = 30) String reason,
            @Size(max = 1000) String detail
    ) {}
}
