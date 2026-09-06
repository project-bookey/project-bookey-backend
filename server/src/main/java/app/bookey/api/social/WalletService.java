package app.bookey.api.social;

import app.bookey.api.social.dto.SocialDtos.ExchangeRequest;
import app.bookey.api.social.dto.SocialDtos.WalletView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletRepository;
import app.bookey.domain.wallet.WalletTransaction;
import app.bookey.domain.wallet.WalletTransactionKind;
import app.bookey.domain.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 재화 지갑 (§14.2). 모든 증감은 원장({@link WalletTransaction})과 함께 커밋된다.
 * 무료 엽서는 KST 자정에 리셋(§14.9 확정), 구독 월 지급은 지갑 접근 시 게으르게 반영.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final SubscriptionService subscriptionService;
    private final BookeyProperties properties;
    private final Clock clock;

    private LocalDate todayKst() {
        return LocalDate.ofInstant(clock.instant(), KST);
    }

    /** 지갑을 잠그고 가져온다 — 없으면 만들고, 날짜 리셋·구독 월 지급을 반영한다. */
    @Transactional
    public Wallet prepared(Long userId) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, todayKst())));
        wallet.resetFreeIfNewDay(todayKst());
        subscriptionService.applyMonthlyGrantIfDue(userId, wallet);
        return wallet;
    }

    @Transactional
    public WalletView view(Long userId) {
        return toView(userId, prepared(userId));
    }

    /** 책갈피 → 엽서(1:1) / 우표(2:1) 교환. */
    @Transactional
    public WalletView exchange(Long userId, ExchangeRequest request) {
        BookeyProperties.Social social = properties.social();
        Wallet wallet = prepared(userId);
        int quantity = request.quantity();
        switch (request.target()) {
            case POSTCARD -> {
                int cost = quantity * social.postcardCostBookmarks();
                if (!wallet.trySpendBookmarks(cost)) {
                    throw ApiException.of(ErrorCode.INSUFFICIENT_BOOKMARK);
                }
                wallet.add(0, quantity, 0);
                record(userId, WalletTransactionKind.EXCHANGE_POSTCARD, -cost, quantity, 0, null, null);
            }
            case STAMP -> {
                int cost = quantity * social.stampCostBookmarks();
                if (!wallet.trySpendBookmarks(cost)) {
                    throw ApiException.of(ErrorCode.INSUFFICIENT_BOOKMARK);
                }
                wallet.add(0, 0, quantity);
                record(userId, WalletTransactionKind.EXCHANGE_STAMP, -cost, 0, quantity, null, null);
            }
        }
        return toView(userId, wallet);
    }

    /** 엽서 발송 비용 지불 — 무료 일일분 우선, 없으면 보유 엽서. 무엇으로도 못 내면 예외. */
    @Transactional
    public void payPostcardSend(Long userId, Wallet wallet, Long postcardId) {
        if (wallet.tryUseFreePostcard(properties.social().postcardDailyFree())) {
            record(userId, WalletTransactionKind.SEND_POSTCARD_FREE, 0, 0, 0, "POSTCARD", postcardId);
            return;
        }
        if (wallet.trySpendPostcard()) {
            record(userId, WalletTransactionKind.SEND_POSTCARD, 0, -1, 0, "POSTCARD", postcardId);
            return;
        }
        throw ApiException.of(ErrorCode.INSUFFICIENT_POSTCARD);
    }

    /** 우표 1개 지불 — 발송 시 동봉(ATTACH_STAMP) 또는 답장(REPLY_STAMP). */
    @Transactional
    public void payStamp(Long userId, Wallet wallet, WalletTransactionKind kind, Long postcardId) {
        if (!wallet.trySpendStamp()) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_STAMP);
        }
        record(userId, kind, 0, 0, -1, "POSTCARD", postcardId);
    }

    /** 관리자 수동 조정 — IAP·제휴 적립이 붙기 전의 베타 운영 경로. 음수 잔액은 만들지 않는다. */
    @Transactional
    public void adminAdjust(Long userId, int bookmarks, int postcards, int stamps) {
        Wallet wallet = prepared(userId);
        if (wallet.getBookmarkBalance() + bookmarks < 0
                || wallet.getPostcardBalance() + postcards < 0
                || wallet.getStampBalance() + stamps < 0) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_BOOKMARK);
        }
        wallet.add(bookmarks, postcards, stamps);
        record(userId, WalletTransactionKind.ADMIN_ADJUST, bookmarks, postcards, stamps, null, null);
    }

    private void record(Long userId, WalletTransactionKind kind,
                        int bookmarkDelta, int postcardDelta, int stampDelta,
                        String refType, Long refId) {
        transactionRepository.save(WalletTransaction.builder()
                .userId(userId).kind(kind)
                .bookmarkDelta(bookmarkDelta).postcardDelta(postcardDelta).stampDelta(stampDelta)
                .refType(refType).refId(refId)
                .build());
    }

    private WalletView toView(Long userId, Wallet wallet) {
        BookeyProperties.Social social = properties.social();
        return new WalletView(
                wallet.getBookmarkBalance(), wallet.getPostcardBalance(), wallet.getStampBalance(),
                wallet.freePostcardsLeft(social.postcardDailyFree()),
                subscriptionService.isActive(userId),
                social.subscriptionPriceKrw());
    }
}
