package app.bookey.api.social.dto;

import app.bookey.domain.social.PostcardStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SocialDtos {

    private SocialDtos() {}

    // ── 지갑 ─────────────────────────────────────────────

    public record WalletView(
            int bookmarkBalance,
            int postcardBalance,
            int stampBalance,
            int freePostcardsLeftToday,
            boolean subscriptionActive,
            int subscriptionPriceKrw
    ) {}

    public enum ExchangeTarget { POSTCARD, STAMP }

    /** 책갈피 → 엽서(1:1) / 우표(2:1) 교환. */
    public record ExchangeRequest(
            @NotNull ExchangeTarget target,
            @Min(1) @Max(1000) int quantity
    ) {}

    // ── 엽서 ─────────────────────────────────────────────

    public record SendPostcardRequest(
            @NotNull Long toUserId,
            @Schema(description = "어떤 독후감을 보고 보냈나 — 수신함에 컨텍스트로 표시") Long postId,
            /** 16글자(grapheme) — 코드 유닛 여유로 Size 는 크게 두고 서버가 글자 수로 검사한다. */
            @NotBlank @Size(max = 128) String body,
            @Schema(description = "우표 동봉 — 내 우표 1개를 부담해 상대가 무료로 답장하게 한다") boolean attachStamp
    ) {}

    public record ReplyPostcardRequest(
            @NotBlank @Size(max = 128) String body
    ) {}

    public record PostcardView(
            @NotNull Long id,
            @NotNull Long fromUserId, @NotNull String fromNickname, String fromAvatarUrl,
            @NotNull Long toUserId, @NotNull String toNickname, String toAvatarUrl,
            Long postId, String postTitle,
            @NotNull String body,
            boolean stampAttached,
            @NotNull PostcardStatus status,
            String replyBody, Instant repliedAt,
            /** 내가 보낸 엽서인가 */
            boolean mine,
            @NotNull Instant createdAt
    ) {}

    // ── 팔로우 ───────────────────────────────────────────

    public record FollowCodeView(@NotNull String code, @NotNull String deepLink) {}

    public record FollowByCodeRequest(@NotBlank @Size(max = 32) String code) {}

    public record FollowUserView(
            @NotNull Long userId,
            @NotNull String nickname,
            String avatarUrl,
            boolean mutual,
            @NotNull Instant followedAt
    ) {}

    // ── 프로필 ───────────────────────────────────────────

    public record UserProfileView(
            @NotNull Long userId,
            @NotNull String nickname,
            String avatarUrl,
            @NotNull String handle,
            long followerCount,
            long followingCount,
            /** 방문 수 — 누구에게나 보인다. "누가" 는 구독 전용(/me/visitors). */
            long visitCount,
            long publicPostCount,
            boolean iFollow,
            boolean followsMe,
            boolean mutual,
            boolean me
    ) {}

    public record VisitorView(
            @NotNull Long userId,
            @NotNull String nickname,
            String avatarUrl,
            @NotNull Instant visitedAt
    ) {}

    public record LikerView(
            @NotNull Long userId,
            @NotNull String nickname,
            String avatarUrl,
            @NotNull Instant likedAt
    ) {}
}
