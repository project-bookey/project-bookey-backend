package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.ExchangeRequest;
import app.bookey.api.social.dto.SocialDtos.WalletView;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Wallet", description = "재화 지갑 — 책갈피 · 엽서 · 우표 (§14.2)")
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "내 지갑 — 잔액 · 오늘 남은 무료 엽서 · 구독 상태")
    @GetMapping
    public WalletView wallet(@AuthenticationPrincipal AuthUser user) {
        return walletService.view(user.id());
    }

    @Operation(summary = "책갈피 교환 — 엽서(1책갈피) · 우표(2책갈피)")
    @PostMapping("/exchange")
    public WalletView exchange(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody ExchangeRequest request) {
        return walletService.exchange(user.id(), request);
    }
}
