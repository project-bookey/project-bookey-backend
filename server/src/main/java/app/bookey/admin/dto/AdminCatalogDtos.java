package app.bookey.admin.dto;

import app.bookey.domain.club.ClubMemberStatus;
import app.bookey.domain.club.ClubRole;
import app.bookey.domain.club.ClubStatus;
import app.bookey.domain.club.ClubVisibility;
import app.bookey.domain.user.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 관리자 도서·모임 관리. */
public final class AdminCatalogDtos {

    private AdminCatalogDtos() {}

    // ── 도서 ────────────────────────────────────────────────

    public record AdminBookCreateRequest(
            /** 하이픈·공백은 서버가 지운다 — 978-89-... 그대로 붙여 넣어도 된다. */
            @Size(max = 20) String isbn13,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 200) String author,
            @Size(max = 200) String publisher,
            @Positive Integer totalPages,
            @Size(max = 500) String coverUrl,
            @Size(max = 100) String category,
            @NotBlank @Size(max = 500) String reason
    ) {}

    /** 이 책을 쓰는 곳 — 병합·수정 전에 영향을 가늠한다. */
    public record BookUsage(
            long readingRecords,
            long reviews,
            long posts,
            long remarks,
            long likes,
            long clubBooks,
            long meetings,
            long pageSuggestions,
            long shareCards,
            boolean editorPick
    ) {}

    public record AdminBookView(
            @NotNull Long id,
            String isbn13,
            @NotNull String title,
            String author,
            String publisher,
            Integer totalPages,
            String coverUrl,
            String category,
            @NotNull String source,
            boolean userCreated,
            @NotNull Instant createdAt,
            @NotNull BookUsage usage
    ) {}

    /** 페이지 수 제안이 모인 책 — 지금 값과 가장 많이 나온 값을 나란히 본다. */
    public record PageSuggestionRow(
            @NotNull Long bookId,
            @NotNull String title,
            Integer currentPages,
            int topPages,
            long topVotes,
            long totalVotes,
            @NotNull Instant lastSuggestedAt
    ) {}

    public record PageSuggestionTally(int pages, long votes) {}

    public record RemovedCountView(int removed) {}

    /** 병합 미리보기 — blockers 가 있으면 병합할 수 없다. */
    public record BookMergePreview(
            @NotNull AdminBookView source,
            @NotNull AdminBookView target,
            /** 두 책을 다 읽은(기록이 있는) 회원 — 원본 쪽 기록의 회차를 뒤로 미룬다. */
            long sharedReaders,
            /** 두 책에 다 좋아요한 회원 — 원본 쪽 좋아요는 지운다. */
            long sharedLikes,
            /** 두 책에 다 페이지 수를 제안한 회원 — 원본 쪽 제안은 지운다. */
            long sharedSuggestions,
            @NotNull List<String> blockers,
            /** 병합은 되지만 회원에게 보일 변화 — 기록은 지우지 않는다. */
            @NotNull List<String> warnings,
            boolean mergeable
    ) {}

    public record BookMergeRequest(@NotNull Long targetId, @NotBlank @Size(max = 500) String reason) {}

    /** 옮긴 행 수. */
    public record BookMergeResult(@NotNull Long targetId, @NotNull BookUsage moved) {}

    // ── 모임 ────────────────────────────────────────────────

    public record AdminClubView(
            @NotNull Long id,
            @NotNull String name,
            String description,
            @NotNull ClubVisibility visibility,
            @NotNull ClubStatus status,
            @NotNull String joinCode,
            int memberCount,
            int memberLimit,
            @NotNull Long ownerId,
            String ownerNickname,
            @NotNull LocalDate startsAt,
            LocalDate endsAt,
            @NotNull Instant createdAt,
            long postCount,
            long meetingCount,
            @NotNull List<String> bookTitles
    ) {}

    public record AdminClubMemberRow(
            @NotNull Long userId,
            String nickname,
            String handle,
            UserStatus userStatus,
            @NotNull ClubRole role,
            @NotNull ClubMemberStatus status,
            @NotNull Instant joinedAt,
            Instant leftAt,
            String kickReason
    ) {}

    public record ClubCodeView(@NotNull String joinCode) {}
}
