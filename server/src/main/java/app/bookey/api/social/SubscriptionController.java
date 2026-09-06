package app.bookey.api.social;

import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionCheckoutRequest;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionCheckoutView;
import app.bookey.api.social.dto.SubscriptionDtos.SubscriptionVerifyRequest;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Subscription", description = "구독 결제 — Apple/Google IAP · Toss 웹 결제 계약")
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "구독 결제 시작 — 결제 SDK/웹 위젯에 넘길 주문 계약")
    @PostMapping("/checkout")
    public SubscriptionCheckoutView checkout(@AuthenticationPrincipal AuthUser user,
                                             @Valid @RequestBody SubscriptionCheckoutRequest request) {
        return subscriptionService.checkout(user.id(), request);
    }

    @Operation(summary = "구독 결제 검증 — IAP 영수증 또는 Toss paymentKey 검증 후 구독 활성화")
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody SubscriptionVerifyRequest request) {
        subscriptionService.verify(user.id(), request);
        return ResponseEntity.noContent().build();
    }
}
