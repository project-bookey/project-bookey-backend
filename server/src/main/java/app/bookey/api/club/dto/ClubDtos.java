package app.bookey.api.club.dto;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.domain.club.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ClubDtos {

    private ClubDtos() {}

    /** 클럽 한 줄 소개 — 머리에 두 줄 안으로 들어가게 짧게만 쓴다. */
    public static final int DESCRIPTION_MAX = 50;

    // ── 생성 · 수정 ───────────────────────────────────────────
    /**
     * 모임은 기간 없이 이어지고 책은 만남마다 고른다. startsAt · endsAt · autoCheckpoints · checkpoints 는
     * 예전 앱이 보내도 무시한다(다음 계약 정리 때 지운다).
     */
    public record CreateClubRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(max = DESCRIPTION_MAX) String description,
            /** 처음 읽을 책(선택) — 주면 지금 읽는 책으로 바로 잡는다. 보통은 첫 만남을 열며 고른다. */
            Long bookId,
            @Deprecated LocalDate startsAt,
            @Deprecated LocalDate endsAt,
            ClubVisibility visibility,
            /** 무료 정원(bookey.club.free-member-limit) 이하만. 더 필요하면 만든 뒤 책갈피로 자리를 늘린다. */
            @Min(2) Integer memberLimit,
            Boolean allowNudge,
            @Deprecated Boolean autoCheckpoints,
            @Deprecated List<CheckpointRequest> checkpoints
    ) {}

    public record CheckpointRequest(
            @Size(max = 60) String title,
            @NotNull @Min(1) Integer targetPage,
            @NotNull Instant dueAt
    ) {}

    public record UpdateClubRequest(
            @Size(max = 60) String name,
            @Size(max = DESCRIPTION_MAX) String description,
            ClubVisibility visibility,
            /** 모임에 기간이 없어 무시한다. */
            @Deprecated LocalDate endsAt,
            Boolean allowNudge
    ) {}

    public record JoinRequest(
            @NotBlank @Size(max = 12) String code,
            /** 모임에 기간이 없어 무시한다. */
            @Deprecated Boolean adoptTargetDate,
            /** 진척 공개 동의 (§12.1 ③). false 면 비공개로 참가. */
            Boolean shareProgress
    ) {}

    public record JoinPublicRequest(
            /** 모임에 기간이 없어 무시한다. */
            @Deprecated Boolean adoptTargetDate,
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
            /** 기간이 있던 예전 모임만 값이 있다. */
            LocalDate endsAt,
            @NotNull ClubStatus status,
            boolean alreadyMember,
            boolean joinable,
            String joinBlockedReason,
            /** 클럽 머리 배경 사진 — 없으면 null. */
            String backgroundUrl
    ) {}

    public record ClubSummaryView(
            @NotNull Long id,
            @NotNull String name,
            String coverUrl,
            BookSummary book,
            @NotNull ClubStatus status,
            int memberCount,
            /** 기간이 없어져 늘 0 이다 — 예전 앱 호환용으로만 남겨 둔다. 다음 모임은 nextMeetingAt. */
            @Deprecated long daysLeft,
            Double myCompletionRate,
            Double averageCompletionRate,
            int unreadPostCount,
            /** 내 역할 — 목록에서 호스트에게만 관리 버튼을 보여주기 위해 내린다. */
            @NotNull ClubRole myRole,
            /** 함께 읽는 사람들 — 진척 높은 순. 목록 카드의 아바타 줄과 '지금 읽는 중' 표시용. */
            @NotNull List<ClubMemberBrief> members,
            /** 다음 만남 시각 — 잡힌 만남이 없으면 null. */
            Instant nextMeetingAt,
            /** 다음 만남 제목 — 잡힌 만남이 없으면 null. */
            String nextMeetingTitle,
            /** 한 줄 소개. */
            String description,
            /** 클럽 머리 배경 사진 — 없으면 null. */
            String backgroundUrl
    ) {}

    /** 목록용 멤버 요약 — 홈의 MemberProgressView 에서 카드에 필요한 만큼만 뽑았다. */
    public record ClubMemberBrief(
            @NotNull Long userId,
            @NotNull String nickname,
            String avatarUrl,
            @NotNull ClubRole role,
            boolean isMe,
            /** 진척 비공개 멤버는 null. */
            Double completionRate,
            /** 열린 읽기 세션이 있으면 true — 진척 비공개 멤버는 늘 false. */
            boolean readingNow
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
            /** 지금 읽는 책 — 다가오는 만남의 책. 책을 고른 만남이 아직 없으면 null. */
            BookSummary book,
            @NotNull LocalDate startsAt,
            /** 기간이 있던 예전 모임만 값이 있다. */
            LocalDate endsAt,
            /** 기간이 없어져 늘 0 이다 — 예전 앱 호환용. */
            @Deprecated long daysLeft,
            int memberCount,
            int memberLimit,
            @NotNull ClubRole myRole,
            boolean myShareProgress,
            boolean myAllowNudge,
            int myRank,
            Double averageCompletionRate,
            List<MemberProgressView> members,
            /** 체크포인트는 걷어냈다 — 늘 빈 목록 · null. 예전 앱 호환용. */
            @Deprecated List<CheckpointView> checkpoints,
            @Deprecated CheckpointView nextCheckpoint,
            @NotNull ClubSeatPolicy seatPolicy,
            /** 모임 전체의 찌르기 허용 여부 — 호스트가 모임 설정에서 바꾼다(myAllowNudge 는 내 개인 설정). */
            boolean allowNudge,
            /** 다음 만남 시각 — 잡힌 만남이 없으면 null. */
            Instant nextMeetingAt,
            /** 클럽 머리 배경 사진 — 호스트가 올린다. 없으면 null. */
            String backgroundUrl
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

    /**
     * 작성자 수정 — 보낸 값으로 전부 바꾼다(생략한 값은 비우는 것으로 본다).
     * 필드가 셋뿐이고 앱이 늘 폼 전체를 들고 있어, 부분 수정보다 이 편이 결과를 예측하기 쉽다.
     * 쪽을 떼려면 anchorPage 를 비우고 spoilerLevel 을 NONE 으로 보낸다.
     */
    public record UpdateClubPostRequest(
            @Size(max = 10000) String body,
            @Min(0) Integer anchorPage,
            SpoilerLevel spoilerLevel
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
            Integer imageHeight,
            /** 작성자가 고친 시각 — null 이면 처음 그대로다. */
            Instant editedAt
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

    /**
     * 주간 공유 카드 — 한 주(월~일, KST) 합산과 대표 조각.
     * 대표 조각·문장은 보는 사람에게 가려지지 않은 것만 싣는다 — 이미지로 밖에 공유되므로.
     */
    public record ClubLogWeekView(
            @NotNull LocalDate weekStart,
            @NotNull LocalDate weekEnd,
            @NotNull String clubName,
            BookSummary book,
            @NotNull ClubLogSummary summary,
            List<ClubPostView> highlights,
            /** 그 주 반응이 가장 많은 인용 글 본문. 없으면 null. */
            String topQuote
    ) {}

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
