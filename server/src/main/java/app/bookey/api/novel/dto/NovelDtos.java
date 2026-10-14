package app.bookey.api.novel.dto;

import app.bookey.domain.novel.*;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class NovelDtos {
    private NovelDtos() {}
    public record CreateNovelRequest(@NotNull NovelKind kind, @NotBlank @Size(max = 120) String title,
            @Size(max = 500) String description, @NotBlank @Size(max = 30) String genre,
            @NotNull Boolean isPublic, @Min(2) @Max(10) Integer memberLimit,
            @Min(1) @Max(50) Integer chapterLimit, @Min(1) @Max(168) Integer turnHours, Long coverId,
            @Schema(description = "릴레이 방식. 비우면 CIRCLE(예전 앱 호환)") NovelMode mode,
            @Schema(description = "OPEN: 회차 하나를 배달할 사람 수") @Min(1) @Max(10) Integer deliveryCount,
            @Schema(description = "CIRCLE: 함께 쓸 팔로우한 작가(본인 빼고 최대 9명). 승인 없이 바로 참가한다")
            @Size(max = 9, message = "함께 쓸 작가는 아홉 명까지 고를 수 있어요.") List<Long> inviteeIds,
            @Schema(description = "줄거리를 가져온 베이스 작품") Long baseWorkId) {}
    public record NovelCoverRequest(Long coverId) {}
    public record NovelMemberDecision(@NotNull Boolean approved) {}
    public record NovelWriteRequest(@NotNull @Min(1) Long turnNumber,
            @NotNull @Size(max = 120) String title, @NotNull @Size(max = 20000) String body) {}
    /** 배달받은 열매에서 뻗는 가지. 낡은 쓰기는 배달 id(경로)와 그 상태로 막는다. */
    public record NovelBranchWriteRequest(@NotNull @Size(max = 120) String title, @NotNull @Size(max = 20000) String body) {}
    public record NovelGenresRequest(@NotNull @Size(max = NovelGenres.MAX_INTERESTS, message = "관심 장르는 다섯 개까지 고를 수 있어요.")
            List<@NotBlank @Size(max = 30) String> genres) {}
    public record NovelCoverView(@NotNull Long id, @NotNull String url, Integer width, Integer height) {}
    public record NovelMemberView(@NotNull Long userId, @NotNull String nickname, String avatarUrl,
            @NotNull String status, boolean owner, boolean currentWriter) {}
    public record NovelSummary(@NotNull Long id, @NotNull NovelKind kind, @NotNull NovelStatus status,
            @NotNull String title, @NotNull String description, @NotNull String genre,
            String coverUrl, @NotNull String ownerNickname,
            @Schema(description = "OPEN 은 회차를 쓴 작가 수, CIRCLE 은 참가자 수") int memberCount,
            @Schema(description = "OPEN 은 제한이 없어 비어 있다") Integer memberLimit,
            int chapterCount, int chapterLimit, boolean isPublic, boolean mine, boolean myTurn,
            Instant turnDueAt, @NotNull Instant createdAt,
            @Schema(description = "탈퇴한 사람을 뺀 좋아요 수") long likeCount,
            @Schema(description = "내가 좋아요를 눌렀는지") boolean liked,
            @NotNull NovelMode mode, @Schema(description = "OPEN: 회차 하나를 배달할 사람 수") Integer deliveryCount) {}
    public record NovelLikeView(boolean liked, long likeCount) {}
    public record NovelDetail(@NotNull NovelSummary novel, List<NovelMemberView> members,
            List<NovelMemberView> applications, @NotNull String myMembership,
            boolean canApply, boolean canWrite, boolean canStart, boolean canComplete,
            long turnNumber, String currentWriterNickname,
            Long coverId,
            @Schema(description = "이 작품에서 내가 들고 있는 열매(배달). 있으면 그 회차에서 가지를 뻗어 이어 쓸 수 있다") Long myDeliveryId,
            Long baseWorkId) {}
    public record NovelChapterSummary(@NotNull Long id, int chapterNumber, @NotNull String title,
            @NotNull String authorNickname, @NotNull String excerpt, @NotNull Instant createdAt) {}
    public record NovelChapterView(@NotNull Long id, @NotNull Long novelId, int chapterNumber,
            @NotNull String title, @NotNull String body, @NotNull String authorNickname,
            @NotNull Instant createdAt, @Schema(description = "뻗어 나온 회차. 뿌리 회차는 비어 있다") Long parentChapterId) {}
    /** 회차 나무의 마디 하나. chapterNumber 는 가지의 깊이다. */
    public record NovelChapterNode(@NotNull Long id, Long parentChapterId, int chapterNumber, @NotNull String title,
            @NotNull String authorNickname, @NotNull String excerpt, @NotNull Instant createdAt) {}
    /** 내가 받은 열매 — 이 회차에서 가지를 뻗어 이어 쓸 수 있다. */
    public record NovelDeliveryView(@NotNull Long deliveryId, @NotNull NovelSummary novel, @NotNull Long chapterId,
            int chapterNumber, @NotNull String chapterTitle, @NotNull Instant dueAt) {}
    public record NovelBaseWorkView(@NotNull Long id, @NotNull String title, @NotNull String genre,
            @NotNull String synopsis, @NotNull String source) {}
    public record NovelDraftView(long turnNumber, @NotNull String title, @NotNull String body, Instant savedAt) {}
}
