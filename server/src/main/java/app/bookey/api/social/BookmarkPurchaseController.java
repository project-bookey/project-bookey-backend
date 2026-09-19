package app.bookey.api.social;

import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseCheckoutRequest;
import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseCheckoutView;
import app.bookey.api.social.dto.BookmarkPurchaseDtos.BookmarkPurchaseVerifyRequest;
import app.bookey.api.social.dto.SocialDtos.WalletView;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Bookmark Purchase", description = "단건 결제 — 책갈피 충전")
@RestController
@RequestMapping("/api/v1/bookmark-purchases")
@RequiredArgsConstructor
public class BookmarkPurchaseController {

    private final BookmarkPurchaseService purchaseService;

    @Operation(summary = "책갈피 단건 결제 시작 — 결제 SDK/웹 위젯에 넘길 주문 계약")
    @PostMapping("/checkout")
    public BookmarkPurchaseCheckoutView checkout(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody BookmarkPurchaseCheckoutRequest request) {
        return purchaseService.checkout(user.id(), request);
    }

    @Operation(summary = "책갈피 단건 결제 검증 — 결제 승인 후 책갈피 적립")
    @PostMapping("/verify")
    public WalletView verify(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody BookmarkPurchaseVerifyRequest request) {
        return purchaseService.verify(user.id(), request);
    }
}
