package app.bookey.api.novel.dto;

import app.bookey.domain.novel.*;
import app.bookey.domain.wallet.SubscriptionStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class NovelDtos {
    private NovelDtos() {}
    public record CreateNovelRequest(@NotNull NovelKind kind, @NotBlank @Size(max = 120) String title,
            @Size(max = 500) String description,
            @Schema(description = "예전 앱 호환용 — genres 를 비우면 이 한 장르로 만든다") @Size(max = 30) String genre,
            @NotNull Boolean isPublic, @Min(2) @Max(10) Integer memberLimit,
            @Min(1) @Max(50) Integer chapterLimit, @Min(1) @Max(168) Integer turnHours, Long coverId,
            @Schema(description = "릴레이 방식. 비우면 CIRCLE(예전 앱 호환)") NovelMode mode,
            @Schema(description = "예전 앱 호환용 — 받기만 하고 쓰지 않는다. 무작위 배달은 없앴다", deprecated = true) Integer deliveryCount,
            @Schema(description = "CIRCLE: 함께 쓸 팔로우한 작가(본인 빼고 최대 9명). 승인 없이 바로 참가한다")
            @Size(max = 9, message = "함께 쓸 작가는 아홉 명까지 고를 수 있어요.") List<Long> inviteeIds,
            @Schema(description = "줄거리를 가져온 베이스 작품") Long baseWorkId,
            @Schema(description = "OPEN: 회차 하나에 붙을 수 있는 가지 수(2~5). 비우면 3. 첫 작가의 줄기는 따로 센다")
            @Min(Novel.BRANCH_LIMIT_MIN) @Max(Novel.BRANCH_LIMIT_MAX) Integer branchLimit,
            @Schema(description = "만든 사람이 쓰는 1회차. 작품과 함께 저장돼 회차 없는 작품이 생기지 않는다")
            @NotNull @Valid FirstChapterRequest firstChapter,
            @Schema(description = "작품 장르(1~3개, 고른 순서). 첫 번째가 대표 장르(genre)가 된다. 겹친 장르는 하나로 센다")
            List<@NotBlank @Size(max = 30) String> genres) {}
    public record FirstChapterRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 20000) String body) {}
    public record NovelCoverRequest(Long coverId) {}
    public record NovelMemberDecision(@NotNull Boolean approved) {}
    public record NovelWriteRequest(@NotNull @Min(1) Long turnNumber,
            @NotNull @Size(max = 120) String title, @NotNull @Size(max = 20000) String body) {}
    /** 자리를 맡은 열매에서 뻗는 가지. 낡은 쓰기는 열매 id(경로)와 그 상태로 막는다. */
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
            @NotNull NovelMode mode,
            @Schema(description = "예전 앱 호환용 — 예전에 회차 하나를 무작위로 배달한 사람 수. 새 작품은 비어 있다", deprecated = true) Integer deliveryCount,
            @Schema(description = "OPEN: 회차 하나에 붙을 수 있는 가지 수") Integer branchLimit,
            @Schema(description = "작품 장르(1~3개, 고른 순서). 첫 번째가 genre 다") @NotNull List<String> genres) {}
    public record NovelLikeView(boolean liked, long likeCount) {}
    /**
     * 홈 열매 하나 — 지금 이어 쓸 수 있는 회차. 누르면 그 회차를 읽고, 쓰기 시작하면 자리를 맡는다.
     * 같은 작품의 다른 회차가 따로 열매로 놓일 수 있다.
     */
    public record NovelHomeFruitView(@NotNull Long chapterId, int chapterNumber, @NotNull String chapterTitle,
            @NotNull String excerpt, @NotNull String authorNickname, @NotNull Instant createdAt,
            @Schema(description = "이 회차에 남은 가지 자리") int slotsLeft,
            @NotNull NovelSummary novel) {}
    /** 홈 열매 밭 — 지금 놓인 묶음과 새로고침 횟수. 무료를 다 쓰면 광고를 보고, 광고 몫도 다 쓰면 결제해 새로고침한다. */
    public record NovelHomeFeedView(
            @Schema(description = "예전 앱 호환용 — fruits 의 작품을 한 번씩, 열매 순서대로", deprecated = true) @NotNull List<NovelSummary> novels,
            @Schema(description = "오늘 남은 무료 새로고침") int freeRefreshesLeftToday,
            @Schema(description = "하루 무료 새로고침 횟수") int dailyFreeRefreshes,
            @Schema(description = "무료·광고 새로고침이 다시 차는 때 — 다음 KST 자정") @NotNull Instant resetsAt,
            @Schema(description = "오늘 남은 광고 새로고침 — 무료가 남아 있어도 센다. KST 자정에 다시 찬다") int adRefreshesLeftToday,
            @Schema(description = "하루 광고 새로고침 횟수") int dailyAdRefreshes,
            @Schema(description = "결제하고 아직 쓰지 않은 새로고침 — 날이 바뀌어도 남는다") int paidRefreshes,
            @Schema(description = "피드 자리에 놓을 열매(이어 쓸 수 있는 회차), 저장된 순서대로. 내가 자리를 맡은 열매·내 차례 작품 뒤에 놓는다")
            @NotNull List<NovelHomeFruitView> fruits) {}
    /** 새로고침 결제 주문 — 오늘 무료·광고 횟수를 다 썼고 결제해 둔 횟수도 없을 때만 연다. */
    public record NovelRefreshCheckoutRequest(@Schema(description = "APPLE·GOOGLE 만") @NotNull SubscriptionStore provider) {}
    public record NovelRefreshCheckoutView(@NotNull SubscriptionStore provider,
            @Schema(description = "스토어에서 살 소모성 상품 — 한 번 사면 새로고침 한 번") @NotNull String productId,
            @NotNull String orderId,
            @Schema(description = "주문에 적어 둔 가격 — 실제 결제 금액은 스토어 가격이다") int amountKrw) {}
    /** 스토어 결제를 마친 뒤 보내는 확인 요청. GOOGLE 은 receiptData(구매 토큰), APPLE 은 originalTransactionId 가 있어야 한다. */
    public record NovelRefreshVerifyRequest(@NotNull SubscriptionStore provider, @NotBlank String orderId, @NotBlank String productId,
            @Schema(description = "GOOGLE: 구매 토큰") String receiptData,
            @Schema(description = "APPLE: transactionId") String originalTransactionId) {}
    public record NovelDetail(@NotNull NovelSummary novel, List<NovelMemberView> members,
            List<NovelMemberView> applications, @NotNull String myMembership,
            boolean canApply,
            @Schema(description = "CIRCLE 은 내 차례인지, OPEN 은 첫 작가가 줄기를 이어 쓸 수 있는지") boolean canWrite,
            boolean canStart, boolean canComplete,
            long turnNumber, String currentWriterNickname,
            Long coverId,
            @Schema(description = "이 작품에서 내가 자리를 맡은 열매. 있으면 그 회차에서 가지를 뻗어 이어 쓸 수 있다") Long myDeliveryId,
            Long baseWorkId) {}
    public record NovelChapterSummary(@NotNull Long id, int chapterNumber, @NotNull String title,
            @NotNull String authorNickname, @NotNull String excerpt, @NotNull Instant createdAt) {}
    public record NovelChapterView(@NotNull Long id, @NotNull Long novelId, int chapterNumber,
            @NotNull String title, @NotNull String body, @NotNull String authorNickname,
            @NotNull Instant createdAt, @Schema(description = "뻗어 나온 회차. 뿌리 회차는 비어 있다") Long parentChapterId,
            @Schema(description = "글쓴이를 뺀, 이 회차를 연 사람 수(한 사람당 한 번)") int viewCount,
            @Schema(description = "내가 쓴 회차라 햇살을 내고 다음 회차를 이어 쓸 수 있는지") boolean canSelfContinue,
            @Schema(description = "내 글 잇기에 드는 햇살") int selfContinueCost,
            @Schema(description = "첫 작가의 줄기 회차인지 — 수거되지 않고 나무가 이 회차들을 따라 위로 자란다") boolean trunk,
            @Schema(description = "내가 이 회차에서 자리를 맡고 이어 쓸 수 있는지") boolean canClaim,
            @Schema(description = "이 회차에 남은 가지 자리. 줄기는 따로 센다") int slotsLeft) {}
    /** 회차 나무의 마디 하나. chapterNumber 는 가지의 깊이다. */
    public record NovelChapterNode(@NotNull Long id, Long parentChapterId, int chapterNumber, @NotNull String title,
            @NotNull String authorNickname, @NotNull String excerpt, @NotNull Instant createdAt,
            @Schema(description = "첫 작가의 줄기 회차인지 — 앱은 이 회차들을 나무의 큰 줄기로 그린다") boolean trunk) {}
    /** 내가 자리를 맡은 열매 — 마감 전에 이 회차에서 가지를 뻗어 이어 쓸 수 있다. */
    public record NovelDeliveryView(@NotNull Long deliveryId, @NotNull NovelSummary novel, @NotNull Long chapterId,
            int chapterNumber, @NotNull String chapterTitle, @NotNull Instant dueAt,
            @Schema(description = "CLAIM: 홈의 열매를 골라 맡은 자리, SELF: 햇살을 내고 내 회차에 이어 쓰는 열매(그만두면 햇살을 돌려받는다), RANDOM: 예전에 무작위로 온 열매")
            @NotNull NovelDeliverySource source) {}
    public record NovelBaseWorkView(@NotNull Long id, @NotNull String title, @NotNull String genre,
            @NotNull String synopsis, @NotNull String source) {}
    /** 나뭇가지 수거함의 가지 하나 — 지울 날(purgeAt) 전에 살리거나 버린다. */
    public record NovelBinBranchView(@NotNull Long chapterId, @NotNull Long novelId, @NotNull String novelTitle,
            int chapterNumber, @NotNull String title, @NotNull String excerpt, int viewCount,
            @NotNull Instant prunedAt, @NotNull Instant purgeAt) {}
    public record NovelBranchBinView(@NotNull List<NovelBinBranchView> branches,
            @Schema(description = "가지 하나를 살리는 데 드는 햇살") int restoreCost) {}
    public record NovelSunlightProductView(@NotNull String productId, int quantity, int amountKrw) {}
    public record NovelSunlightView(int balance, int restoreCost, int selfBranchCost,
            @NotNull List<NovelSunlightProductView> products) {}
    public record NovelSunlightCheckoutRequest(@NotNull SubscriptionStore provider, @NotBlank String productId) {}
    public record NovelSunlightCheckoutView(@NotNull SubscriptionStore provider, @NotNull String productId,
            @NotNull String orderId, int quantity, int amountKrw) {}
    public record NovelSunlightVerifyRequest(@NotNull SubscriptionStore provider, @NotBlank String orderId,
            @NotBlank String productId, String receiptData, String originalTransactionId) {}
    public record NovelDraftView(long turnNumber, @NotNull String title, @NotNull String body, Instant savedAt) {}
}
