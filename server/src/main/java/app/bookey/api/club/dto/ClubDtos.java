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
            /** 무료 정원(bookey.club.free-member-limit) 이하만. 더 필요하면 만든 뒤 책갈피로 자리를 늘린다. */
            @Min(2) Integer memberLimit,
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

    public record JoinPublicRequest(
            Boolean adoptTargetDate,
            /** 진척 공개 동의 (§12.1 ③). false 면 비공개로 참가. */
            Boolean shareProgress
    ) {}

    public record UpdateSharingRequest(Boolean shareProgress, Boolean allowNudge) {}

    public record KickRequest(@NotNull Long userId, @NotBlank @Size(max = 200) String reason) {}

    public record TransferHostRequest(@NotNull Long userId) {}

    // ── 자리 늘리기 ───────────────────────────────────────────
    /** 목표 정원 — 현재 정원보다 크고 최대 정원 이하. 차이만큼 책갈피를 쓴다. */
    public record ExpandSeatsRequest(@NotNull @Min(3) Integer targetLimit) {}

    public record ClubSeatResult(int memberLimit, int bookmarkBalance) {}

    /** 앱이 가격·상한을 하드코딩하지 않도록 모임 홈에 함께 내린다. */
    public record ClubSeatPolicy(int freeLimit, int maxLimit, int costPerSeat) {}

    // ── 조회 ─────────────────────────────────────────────────

    /** 코드로 볼 수 있는 정보는 미리보기 수준까지만 (§8.5). */
    public record ClubPreview(
            @NotNull Long id,
            @NotNull String name,
            String description,
            BookSummary book,
            String hostNickname,
            int memberCount,
            int memberLimit,
            @NotNull LocalDate startsAt,
            @NotNull LocalDate endsAt,
            @NotNull ClubStatus status,
            boolean alreadyMember,
            boolean joinable,
            String joinBlockedReason
    ) {}

    public record ClubSummaryView(
            @NotNull Long id,
            @NotNull String name,
            String coverUrl,
            BookSummary book,
            @NotNull ClubStatus status,
            int memberCount,
            long daysLeft,
            Double myCompletionRate,
            Double averageCompletionRate,
            int unreadPostCount
    ) {}

    public record MemberProgressView(
            @NotNull Long userId,
            @NotNull Long clubMemberId,
            @NotNull String nickname,
            String avatarUrl,
            @NotNull ClubRole role,
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
            @NotNull Long id,
            short seq,
            @NotNull String title,
            int targetPage,
            @NotNull Instant dueAt,
            boolean evaluated,
            long achievedCount,
            long memberCount,
            Boolean myAchieved
    ) {}

    public record ClubHomeView(
            @NotNull Long id,
            @NotNull String name,
            String description,
            String coverUrl,
            @NotNull String joinCode,  // 호스트/멤버에게만 노출
            @NotNull ClubVisibility visibility,
            @NotNull ClubStatus status,
            BookSummary book,
            @NotNull LocalDate startsAt,
            @NotNull LocalDate endsAt,
            long daysLeft,
            int memberCount,
            int memberLimit,
            @NotNull ClubRole myRole,
            boolean myShareProgress,
            boolean myAllowNudge,
            int myRank,
            Double averageCompletionRate,
            List<MemberProgressView> members,
            List<CheckpointView> checkpoints,
            CheckpointView nextCheckpoint,
            @NotNull ClubSeatPolicy seatPolicy
    ) {}

    public record ClubResultView(
            @NotNull Long clubId,
            @NotNull String name,
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
    public record CreateClubPostRequest(
            ClubPostType type,
            @NotBlank @Size(max = 10000) String body,
            @Min(0) Integer anchorPage,
            SpoilerLevel spoilerLevel,
            Long parentId,
            Long linkedPostId
    ) {}

    public record ClubPostView(
            @NotNull Long id,
            Long parentId,
            @NotNull ClubPostType type,
            @NotNull Long authorId,
            @NotNull String authorNickname,
            String authorAvatarUrl,
            /** 마스킹된 글은 body 가 null 이다 — 서버가 애초에 내려보내지 않는다(§8.5). */
            String body,
            boolean masked,
            Integer anchorPage,
            @NotNull SpoilerLevel spoilerLevel,
            boolean pinned,
            int commentCount,
            int reactionCount,
            List<String> myReactions,
            @NotNull Instant createdAt,
            List<ClubPostView> comments,
            /** 읽기로그 조각 사진. 가려진 조각은 본문과 함께 null 이다. */
            String imageUrl,
            Integer imageWidth,
            Integer imageHeight
    ) {}

    // ── 읽기로그 ─────────────────────────────────────────────
    /** 하루 보드 — 그날의 조각과 모임 합산. */
    public record ClubLogDayView(
            @NotNull LocalDate date,
            List<ClubPostView> logs,
            @NotNull ClubLogSummary summary
    ) {}

    /** 그날 끝난 세션 합산(진척 공개 멤버만) + 조각 수. */
    public record ClubLogSummary(long pagesRead, long durationSec, long readerCount, int logCount) {}

    /** 요일 스트립 한 칸. */
    public record ClubLogDayCount(@NotNull LocalDate date, int logCount) {}

    /** 지금 읽는 중인 멤버 — 열린 독서 세션이 있는 사람. */
    public record ReadingNowView(
            @NotNull Long userId,
            @NotNull String nickname,
            String avatarUrl,
            @NotNull Instant startedAt
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
