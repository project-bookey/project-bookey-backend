package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.domain.novel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 햇살 — 소설 전용 재화. 잔액이 바뀔 때마다 원장에 한 줄씩 남긴다. 부르는 쪽 트랜잭션 안에서 지갑 행을 잠근다. */
@Service @RequiredArgsConstructor
public class NovelSunlightService {
    private final NovelSunlightWalletRepository wallets;
    private final NovelSunlightTransactionRepository transactions;
    private final BookeyProperties properties;

    @Transactional(readOnly = true)
    public NovelSunlightView view(Long me) {
        int balance = wallets.findById(me).map(NovelSunlightWallet::getBalance).orElse(0);
        return view(balance);
    }
    public NovelSunlightView view(int balance) {
        BookeyProperties.Novel.Sunlight config = config();
        return new NovelSunlightView(balance, config.restoreCost(), config.selfBranchCost(),
                config.products().stream().map(p -> new NovelSunlightProductView(p.productId(), p.quantity(), p.amountKrw())).toList());
    }
    public int restoreCost() { return config().restoreCost(); }
    public int selfBranchCost() { return config().selfBranchCost(); }

    /** 모자라면 402 — 앱이 충전 화면을 연다. */
    public int spend(Long me, int amount, NovelSunlightTransactionKind kind, String refType, Long refId) {
        NovelSunlightWallet wallet = locked(me);
        if (!wallet.trySpend(amount)) throw ApiException.of(ErrorCode.NOVEL_SUNLIGHT_INSUFFICIENT);
        transactions.save(new NovelSunlightTransaction(me, kind, -amount, refType, refId));
        return wallet.getBalance();
    }
    public int grant(Long me, int amount, NovelSunlightTransactionKind kind, String refType, Long refId) {
        if (amount <= 0) return wallets.findById(me).map(NovelSunlightWallet::getBalance).orElse(0);
        NovelSunlightWallet wallet = locked(me);
        wallet.add(amount);
        transactions.save(new NovelSunlightTransaction(me, kind, amount, refType, refId));
        return wallet.getBalance();
    }
    private NovelSunlightWallet locked(Long me) {
        return wallets.lockByUserId(me).orElseGet(() -> wallets.saveAndFlush(new NovelSunlightWallet(me)));
    }
    private BookeyProperties.Novel.Sunlight config() { return properties.novel().sunlight(); }
}
