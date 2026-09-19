package app.bookey.api.social.dto;

import app.bookey.domain.wallet.SubscriptionStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class BookmarkPurchaseDtos {

    private BookmarkPurchaseDtos() {}

    public record BookmarkPurchaseCheckoutRequest(
            @NotNull SubscriptionStore provider,
            @Min(1) @Max(999) int quantity
    ) {}

    public record BookmarkPurchaseCheckoutView(
            @NotNull SubscriptionStore provider,
            @NotBlank String productId,
            @NotBlank String orderId,
            int quantity,
            int bonusQuantity,
            int totalQuantity,
            int amountKrw,
            @NotBlank String customerKey,
            String checkoutUrl,
            String tossClientKey,
            String successUrl,
            String failUrl
    ) {}

    public record BookmarkPurchaseVerifyRequest(
            @NotNull SubscriptionStore provider,
            @NotBlank String productId,
            @NotBlank String orderId,
            @Min(1) @Max(999) int quantity,
            int amountKrw,
            String paymentKey,
            String receiptData,
            String originalTransactionId
    ) {}
}
