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
            @Min(1) @Max(100) Integer chapterLimit, @Min(1) @Max(168) Integer turnHours, Long coverId) {}
    public record NovelCoverRequest(Long coverId) {}
    public record NovelJoinRequest(@NotBlank @Size(max = 32) String inviteCode) {}
    public record NovelMemberDecision(@NotNull Boolean approved) {}
    public record NovelWriteRequest(@NotNull @Min(1) Long turnNumber,
            @NotNull @Size(max = 120) String title, @NotNull @Size(max = 20000) String body) {}
    public record NovelCoverView(@NotNull Long id, @NotNull String url, Integer width, Integer height) {}
    public record NovelMemberView(@NotNull Long userId, @NotNull String nickname, String avatarUrl,
            @NotNull String status, boolean owner, boolean currentWriter) {}
    public record NovelSummary(@NotNull Long id, @NotNull NovelKind kind, @NotNull NovelStatus status,
            @NotNull String title, @NotNull String description, @NotNull String genre,
            String coverUrl, @NotNull String ownerNickname, int memberCount, int memberLimit,
            int chapterCount, int chapterLimit, boolean isPublic, boolean mine, boolean myTurn,
            Instant turnDueAt, @NotNull Instant createdAt) {}
    public record NovelDetail(@NotNull NovelSummary novel, List<NovelMemberView> members,
            List<NovelMemberView> applications, @NotNull String myMembership,
            boolean canApply, boolean canWrite, boolean canStart, boolean canComplete,
            long turnNumber, String currentWriterNickname,
            @Schema(description = "개설자와 승인된 참가자에게만 표시") String inviteCode, Long coverId) {}
    public record NovelChapterSummary(@NotNull Long id, int chapterNumber, @NotNull String title,
            @NotNull String authorNickname, @NotNull String excerpt, @NotNull Instant createdAt) {}
    public record NovelChapterView(@NotNull Long id, @NotNull Long novelId, int chapterNumber,
            @NotNull String title, @NotNull String body, @NotNull String authorNickname,
            @NotNull Instant createdAt) {}
    public record NovelDraftView(long turnNumber, @NotNull String title, @NotNull String body, Instant savedAt) {}
}
