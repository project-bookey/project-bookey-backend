package app.bookey.admin.dto;

import app.bookey.domain.legal.ConsentKind;
import app.bookey.domain.user.AuthProvider;
import app.bookey.domain.user.DevicePlatform;
import app.bookey.domain.wallet.BookmarkPurchaseStatus;
import app.bookey.domain.wallet.SubscriptionStatus;
import app.bookey.domain.wallet.SubscriptionStore;
import app.bookey.domain.wallet.WalletTransactionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 회원 CS(지갑·결제·기기·동의) 조회용 응답. 앱 쪽 같은 이름의 DTO 와 스키마 이름이 겹치지 않게 Admin 접두를 붙인다.
 */
public final class AdminCsDtos {

    private AdminCsDtos() {}

    public record AdminWalletSummary(int bookmarks, int postcards, int stamps) {}

    public record AdminSubscriptionRow(
            @NotNull Long id,
            @NotNull SubscriptionStore store,
            @NotNull String productId,
            @NotNull SubscriptionStatus status,
            @NotNull Instant currentPeriodStart,
            @NotNull Instant currentPeriodEnd,
            @NotNull Instant createdAt
    ) {}

    /** tokenTail — 푸시 토큰 끝 6자리만. 토큰 전체는 내려보내지 않는다. */
    public record AdminDeviceRow(
            @NotNull DevicePlatform platform,
            boolean pushEnabled,
            String tokenTail,
            Instant lastSeenAt,
            Instant createdAt
    ) {}

    /** 소셜 연동 — 제공자 쪽 회원 번호(providerUid)는 내려보내지 않는다. */
    public record AdminIdentityRow(@NotNull AuthProvider provider, Instant linkedAt) {}

    /** 동의 종류별 가장 최근 결정. */
    public record AdminConsentRow(@NotNull ConsentKind kind, boolean agreed, String version, Instant decidedAt) {}

    public record AdminWalletTransactionRow(
            @NotNull Long id,
            @NotNull WalletTransactionKind kind,
            int bookmarkDelta,
            int postcardDelta,
            int stampDelta,
            String refType,
            Long refId,
            @NotNull Instant createdAt
    ) {}

    public record AdminBookmarkPurchaseRow(
            @NotNull Long id,
            @NotNull Long userId,
            String userNickname,
            @NotNull SubscriptionStore provider,
            @NotNull String productId,
            @NotNull String orderId,
            int quantity,
            int bonusQuantity,
            int amountKrw,
            @NotNull BookmarkPurchaseStatus status,
            @NotNull Instant createdAt,
            Instant updatedAt
    ) {}

    /** 사유만 받는 조치(세션 끊기 등). */
    public record AdminReasonRequest(@NotBlank @Size(max = 500) String reason) {}
}
