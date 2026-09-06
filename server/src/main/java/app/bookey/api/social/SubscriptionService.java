package app.bookey.api.social;

import app.bookey.common.config.BookeyProperties;
import app.bookey.domain.wallet.Subscription;
import app.bookey.domain.wallet.SubscriptionRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletTransaction;
import app.bookey.domain.wallet.WalletTransactionKind;
import app.bookey.domain.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 구독 (§14.2, 월 17,900원). MVP 는 관리자 지급(ADMIN 스토어)이 유일한 활성화 경로 —
 * 스토어 IAP 영수증 검증이 붙으면 APPLE/GOOGLE 경로가 추가된다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SubscriptionRepository subscriptionRepository;
    private final WalletTransactionRepository transactionRepository;
    private final BookeyProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isActive(Long userId) {
        return subscriptionRepository.findTopByUserIdOrderByIdDesc(userId)
                .map(sub -> sub.isActiveAt(clock.instant()))
                .orElse(false);
    }

    /**
     * 이번 구독 기간의 월 재화(엽서 50 · 우표 30)를 아직 안 받았으면 지급한다 — 지갑 접근 시점에 게으르게.
     * 멱등: lastGrantPeriodStart 가 현재 기간과 같으면 재지급하지 않는다.
     */
    @Transactional
    public void applyMonthlyGrantIfDue(Long userId, Wallet wallet) {
        subscriptionRepository.findTopByUserIdOrderByIdDesc(userId).ifPresent(sub -> {
            if (!sub.isActiveAt(clock.instant()) || !sub.needsMonthlyGrant()) {
                return;
            }
            BookeyProperties.Social social = properties.social();
            wallet.add(0, social.subscriptionMonthlyPostcards(), social.subscriptionMonthlyStamps());
            transactionRepository.save(WalletTransaction.builder()
                    .userId(userId)
                    .kind(WalletTransactionKind.SUBSCRIPTION_GRANT)
                    .postcardDelta(social.subscriptionMonthlyPostcards())
                    .stampDelta(social.subscriptionMonthlyStamps())
                    .refType("SUBSCRIPTION").refId(sub.getId())
                    .build());
            sub.markGranted();
        });
    }

    /** 관리자 지급 — 기존 구독이 있으면 지금부터 months 개월로 연장한다. */
    @Transactional
    public void adminGrant(Long userId, int months) {
        Instant now = clock.instant();
        Instant end = ZonedDateTime.ofInstant(now, KST).plusMonths(months).toInstant();
        subscriptionRepository.findTopByUserIdOrderByIdDesc(userId).ifPresentOrElse(
                sub -> sub.extend(now, end),
                () -> subscriptionRepository.save(Subscription.builder()
                        .userId(userId)
                        .store(SubscriptionStore.ADMIN)
                        .productId("admin.grant")
                        .currentPeriodStart(now)
                        .currentPeriodEnd(end)
                        .build()));
    }

    @Transactional
    public void adminRevoke(Long userId) {
        subscriptionRepository.findTopByUserIdOrderByIdDesc(userId)
                .ifPresent(sub -> sub.cancel(clock.instant()));
    }
}
