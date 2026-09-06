package app.bookey.api.social.dto;

import app.bookey.domain.wallet.SubscriptionStore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class SubscriptionDtos {

    private SubscriptionDtos() {}

    public record SubscriptionCheckoutRequest(
            @NotNull SubscriptionStore provider
    ) {}

    public record SubscriptionCheckoutView(
            @NotNull SubscriptionStore provider,
            @NotBlank String productId,
            @NotBlank String orderId,
            int amountKrw,
            @NotBlank String customerKey,
            String tossClientKey,
            String successUrl,
            String failUrl
    ) {}

    public record SubscriptionVerifyRequest(
            @NotNull SubscriptionStore provider,
            @NotBlank String productId,
            String orderId,
            Integer amountKrw,
            String paymentKey,
            String receiptData,
            String originalTransactionId
    ) {}
}
