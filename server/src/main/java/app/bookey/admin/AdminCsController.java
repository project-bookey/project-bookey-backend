package app.bookey.admin;

import app.bookey.admin.dto.AdminCsDtos.AdminBookmarkPurchaseRow;
import app.bookey.admin.dto.AdminCsDtos.AdminReasonRequest;
import app.bookey.admin.dto.AdminCsDtos.AdminSubscriptionRow;
import app.bookey.admin.dto.AdminCsDtos.AdminWalletTransactionRow;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.wallet.BookmarkPurchaseStatus;
import app.bookey.domain.wallet.SubscriptionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 회원 CS — 지갑·결제 내역 조회와 세션 끊기 (§F13). */
@Tag(name = "Admin CS", description = "회원 지갑·결제 내역 · 세션")
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class AdminCsController {

    private final AdminCsService csService;
    private final AdminUserService adminUserService;

    @Operation(summary = "지갑 원장 — 최근 순. 결제 열람 권한 필요, 첫 쪽 조회는 열람 기록이 남는다")
    @GetMapping("/users/{userId}/wallet-transactions")
    public PageResponse<AdminWalletTransactionRow> walletTransactions(@AuthenticationPrincipal AuthAdmin admin,
                                                                      @PathVariable Long userId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return csService.walletTransactions(admin, userId, page, size);
    }

    @Operation(summary = "구독 이력 — 최근 순. 결제 열람 권한 필요")
    @GetMapping("/users/{userId}/subscriptions")
    public List<AdminSubscriptionRow> subscriptions(@AuthenticationPrincipal AuthAdmin admin,
                                                    @PathVariable Long userId) {
        return csService.subscriptions(admin, userId);
    }

    @Operation(summary = "회원의 책갈피 구매 내역 — 최근 순. 결제 열람 권한 필요")
    @GetMapping("/users/{userId}/bookmark-purchases")
    public PageResponse<AdminBookmarkPurchaseRow> userPurchases(@AuthenticationPrincipal AuthAdmin admin,
                                                                @PathVariable Long userId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return csService.userPurchases(admin, userId, page, size);
    }

    @Operation(summary = "책갈피 구매 검색 — 주문번호(앞부분 일치)·상태·결제 수단")
    @GetMapping("/bookmark-purchases")
    public PageResponse<AdminBookmarkPurchaseRow> purchases(@AuthenticationPrincipal AuthAdmin admin,
                                                            @RequestParam(required = false) String orderId,
                                                            @RequestParam(required = false) BookmarkPurchaseStatus status,
                                                            @RequestParam(required = false) SubscriptionStore provider,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return csService.searchPurchases(admin, orderId, status, provider, page, size);
    }

    @Operation(summary = "회원 로그인 모두 끊기 — 기기 분실·도용 신고 대응. 사유 필수")
    @PostMapping("/users/{userId}/sessions/revoke")
    public ResponseEntity<Void> revokeSessions(@AuthenticationPrincipal AuthAdmin admin,
                                               @PathVariable Long userId,
                                               @Valid @RequestBody AdminReasonRequest request) {
        adminUserService.revokeSessions(admin, userId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
