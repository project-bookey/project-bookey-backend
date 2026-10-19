package app.bookey.api.novel;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelSunlightServiceTest {
    private final NovelSunlightWalletRepository wallets = mock(NovelSunlightWalletRepository.class);
    private final NovelSunlightTransactionRepository transactions = mock(NovelSunlightTransactionRepository.class);
    private final BookeyProperties properties = new BookeyProperties(null, null, null, null, null, null, null, null,
            null, null, new BookeyProperties.Novel(2, 3, 7, null, new BookeyProperties.Novel.Sunlight(3, 5,
                    List.of(new BookeyProperties.Novel.Sunlight.Product("relaynovel.sunlight.10", 10, 1100)),
                    "app.relaynovel.mobile", "app.relaynovel.mobile")));
    private final NovelSunlightService service = new NovelSunlightService(wallets, transactions, properties);
    private final NovelSunlightWallet wallet = new NovelSunlightWallet(1L);

    @BeforeEach void setup() {
        when(wallets.lockByUserId(1L)).thenReturn(Optional.of(wallet));
    }

    @Test @DisplayName("햇살을 쓰면 잔액이 줄고 원장에 음수로 남는다")
    void spendRecordsNegativeAmount() {
        wallet.add(10);
        assertThat(service.spend(1L, 3, NovelSunlightTransactionKind.RESTORE_BRANCH, "NOVEL_CHAPTER", 200L)).isEqualTo(7);
        verify(transactions).save(argThat(t -> t.getAmount() == -3
                && t.getKind() == NovelSunlightTransactionKind.RESTORE_BRANCH && t.getRefId() == 200L));
    }
    @Test @DisplayName("햇살이 모자라면 402로 거절하고 잔액과 원장을 건드리지 않는다")
    void insufficientSunlightIsRejected() {
        wallet.add(2);
        assertThatThrownBy(() -> service.spend(1L, 3, NovelSunlightTransactionKind.SELF_BRANCH, "NOVEL_DELIVERY", 7L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_SUNLIGHT_INSUFFICIENT);
        assertThat(wallet.getBalance()).isEqualTo(2);
        verify(transactions, never()).save(any());
    }
    @Test @DisplayName("지갑이 없으면 처음 채울 때 만든다")
    void walletIsCreatedOnFirstUse() {
        when(wallets.lockByUserId(2L)).thenReturn(Optional.empty());
        when(wallets.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.grant(2L, 10, NovelSunlightTransactionKind.PURCHASE, "NOVEL_SUNLIGHT_PURCHASE", 1L)).isEqualTo(10);
    }
    @Test @DisplayName("잔액과 함께 쓰는 값·충전 상품을 내려준다")
    void viewShowsCostsAndProducts() {
        when(wallets.findById(1L)).thenReturn(Optional.of(wallet));
        wallet.add(4);
        var view = service.view(1L);
        assertThat(view.balance()).isEqualTo(4);
        assertThat(view.restoreCost()).isEqualTo(3);
        assertThat(view.selfBranchCost()).isEqualTo(5);
        assertThat(view.products()).extracting("productId").containsExactly("relaynovel.sunlight.10");
    }
}
