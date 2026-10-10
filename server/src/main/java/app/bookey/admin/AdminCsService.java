package app.bookey.admin;

import app.bookey.admin.dto.AdminCsDtos.AdminBookmarkPurchaseRow;
import app.bookey.admin.dto.AdminCsDtos.AdminSubscriptionRow;
import app.bookey.admin.dto.AdminCsDtos.AdminWalletTransactionRow;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.wallet.BookmarkPurchase;
import app.bookey.domain.wallet.BookmarkPurchaseRepository;
import app.bookey.domain.wallet.BookmarkPurchaseStatus;
import app.bookey.domain.wallet.SubscriptionRepository;
import app.bookey.domain.wallet.SubscriptionStore;
import app.bookey.domain.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 회원 CS — 지갑 원장·구독 이력·책갈피 구매 조회.
 * 결제 정보는 결제 열람 권한(CS 담당 이상)만 볼 수 있고, 회원별 조회는 열람 기록(VIEW_USER_PAYMENTS)을 남긴다.
 * 지갑은 WalletService 를 거치지 않는다 — 그쪽은 지갑이 없으면 만들고 월간 지급을 돌리는 부작용이 있다.
 */
@Service
@RequiredArgsConstructor
public class AdminCsService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final WalletTransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BookmarkPurchaseRepository purchaseRepository;
    private final AdminAuditService auditService;

    @Transactional
    public PageResponse<AdminWalletTransactionRow> walletTransactions(AuthAdmin admin, Long userId, int page, int size) {
        requirePayments(admin);
        requireUser(userId);
        if (page == 0) {
            logView(admin, userId, "walletTransactions");
        }
        return PageResponse.of(
                transactionRepository.findAllByUserIdOrderByIdDesc(userId, PageRequest.of(page, clamp(size))),
                t -> new AdminWalletTransactionRow(t.getId(), t.getKind(), t.getBookmarkDelta(), t.getPostcardDelta(),
                        t.getStampDelta(), t.getRefType(), t.getRefId(), t.getCreatedAt()));
    }

    @Transactional
    public List<AdminSubscriptionRow> subscriptions(AuthAdmin admin, Long userId) {
        requirePayments(admin);
        requireUser(userId);
        logView(admin, userId, "subscriptions");
        return subscriptionRepository.findAllByUserIdOrderByIdDesc(userId).stream()
                .map(AdminUserService::toSubscriptionRow)
                .toList();
    }

    @Transactional
    public PageResponse<AdminBookmarkPurchaseRow> userPurchases(AuthAdmin admin, Long userId, int page, int size) {
        requirePayments(admin);
        requireUser(userId);
        if (page == 0) {
            logView(admin, userId, "bookmarkPurchases");
        }
        return search(BookmarkPurchaseRepository.matching(userId, null, null, null), page, size);
    }

    /** 주문번호·상태·결제 수단으로 전체 회원의 책갈피 구매를 찾는다 — 결제 문의에 주문번호만 있을 때. */
    @Transactional(readOnly = true)
    public PageResponse<AdminBookmarkPurchaseRow> searchPurchases(AuthAdmin admin, String orderId,
                                                                  BookmarkPurchaseStatus status,
                                                                  SubscriptionStore provider, int page, int size) {
        requirePayments(admin);
        String normalized = orderId == null || orderId.isBlank() ? null : orderId.trim();
        return search(BookmarkPurchaseRepository.matching(null, normalized, status, provider), page, size);
    }

    private PageResponse<AdminBookmarkPurchaseRow> search(
            Specification<BookmarkPurchase> spec, int page, int size) {
        var result = purchaseRepository.findAll(spec,
                PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> nicknames = userRepository.findAllById(
                        result.getContent().stream().map(BookmarkPurchase::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
        return PageResponse.of(result, p -> new AdminBookmarkPurchaseRow(p.getId(), p.getUserId(),
                nicknames.get(p.getUserId()), p.getProvider(), p.getProductId(), p.getOrderId(), p.getQuantity(),
                p.getBonusQuantity(), p.getAmountKrw(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt()));
    }

    private void logView(AuthAdmin admin, Long userId, String list) {
        auditService.log(admin, "VIEW_USER_PAYMENTS", "USER", userId, null, null, Map.of("list", list));
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
    }

    private static void requirePayments(AuthAdmin admin) {
        if (!admin.role().canViewPayments()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }

    private static int clamp(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
