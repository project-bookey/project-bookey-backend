package app.bookey.admin;

import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.wallet.BookmarkPurchaseRepository;
import app.bookey.domain.wallet.SubscriptionRepository;
import app.bookey.domain.wallet.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCsServiceTest {

    private static final AuthAdmin SUPPORT = new AuthAdmin(2L, "cs@bookey.app", AdminRole.SUPPORT);
    private static final AuthAdmin VIEWER = new AuthAdmin(3L, "view@bookey.app", AdminRole.VIEWER);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final AdminCsService service = new AdminCsService(userRepository, transactionRepository,
            mock(SubscriptionRepository.class), mock(BookmarkPurchaseRepository.class), auditService);

    @Test
    @DisplayName("결제·지갑 내역은 보기 전용(VIEWER)이 볼 수 없다")
    void viewerForbidden() {
        assertThatThrownBy(() -> service.walletTransactions(VIEWER, 10L, 0, 20))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        assertThatThrownBy(() -> service.subscriptions(VIEWER, 10L))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        assertThatThrownBy(() -> service.searchPurchases(VIEWER, "ORD", null, null, 0, 20))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("지갑 원장은 첫 쪽을 볼 때만 열람 기록을 남긴다 — 쪽을 넘길 때마다 쌓이지 않게")
    void logsOnlyFirstPage() {
        when(userRepository.existsById(10L)).thenReturn(true);
        when(transactionRepository.findAllByUserIdOrderByIdDesc(eq(10L), any())).thenReturn(Page.empty());

        service.walletTransactions(SUPPORT, 10L, 0, 20);
        verify(auditService).log(eq(SUPPORT), eq("VIEW_USER_PAYMENTS"), eq("USER"), eq(10L), isNull(), isNull(),
                eq(Map.of("list", "walletTransactions")));

        service.walletTransactions(SUPPORT, 10L, 1, 20);
        verify(auditService, times(1)).log(any(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("없는 회원이면 NOT_FOUND")
    void missingUser() {
        when(userRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.subscriptions(SUPPORT, 99L))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }
}
