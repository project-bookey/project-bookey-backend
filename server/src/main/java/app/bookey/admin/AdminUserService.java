package app.bookey.admin;

import app.bookey.admin.dto.AdminCsDtos.AdminConsentRow;
import app.bookey.admin.dto.AdminCsDtos.AdminDeviceRow;
import app.bookey.admin.dto.AdminCsDtos.AdminIdentityRow;
import app.bookey.admin.dto.AdminCsDtos.AdminSubscriptionRow;
import app.bookey.admin.dto.AdminCsDtos.AdminWalletSummary;
import app.bookey.admin.dto.AdminDtos.*;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.admin.support.PrivacyMasker;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.api.club.ClubService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.notification.NotificationService.NotificationRequest;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.security.AccessRevocations;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.SanctionPolicy;
import app.bookey.domain.admin.SanctionType;
import app.bookey.domain.admin.UserSanction;
import app.bookey.domain.admin.UserSanctionRepository;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubMemberStatus;
import app.bookey.domain.legal.ConsentKind;
import app.bookey.domain.legal.UserConsent;
import app.bookey.domain.legal.UserConsentRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.reading.ReadingStatus;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.RefreshTokenRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserDevice;
import app.bookey.domain.user.UserDeviceRepository;
import app.bookey.domain.user.UserIdentityRepository;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import app.bookey.domain.wallet.Subscription;
import app.bookey.domain.wallet.SubscriptionRepository;
import app.bookey.domain.wallet.Wallet;
import app.bookey.domain.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final DateTimeFormatter NOTICE_DATE =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));
    /** 닉네임 유일 인덱스(V48)와 같은 규칙 — 겹치면 '_회원번호' 를 붙인다. */
    private static final int NICKNAME_BASE_MAX = 40;

    private final UserRepository userRepository;
    private final ReadingRecordRepository recordRepository;
    private final ReadingSessionRepository sessionRepository;
    private final ReviewRepository reviewRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final UserSanctionRepository sanctionRepository;
    private final AdminAuditService auditService;
    private final app.bookey.api.social.SubscriptionService subscriptionService;
    private final app.bookey.api.social.WalletService walletService;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDeviceRepository deviceRepository;
    private final AccessRevocations accessRevocations;
    private final ClubService clubService;
    private final NotificationService notificationService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserIdentityRepository identityRepository;
    private final UserConsentRepository consentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserRow> search(AuthAdmin admin, String keyword, UserStatus status,
                                        int page, int size) {
        String normalized = emptyToNull(keyword);
        var pageable = PageRequest.of(page, size);
        var result = status == null
                ? userRepository.searchByKeyword(normalized, pageable)
                : userRepository.searchByKeywordAndStatus(normalized, status, pageable);
        return PageResponse.of(result, user -> new UserRow(
                user.getId(), user.getHandle(), user.getNickname(),
                PrivacyMasker.email(user.getEmail()), user.getStatus(), user.getCreatedAt(),
                recordRepository.countByUserIdAndStatus(user.getId(), ReadingStatus.READING),
                recordRepository.countByUserIdAndStatus(user.getId(), ReadingStatus.FINISHED)));
    }

    /**
     * 회원 상세. 이메일 전체 보기는 사유를 남겨야 하며, 그 자체가 감사 로그 대상이다(§F13).
     */
    @Transactional
    public UserDetailView detail(AuthAdmin admin, Long userId, String revealReason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        boolean reveal = revealReason != null && !revealReason.isBlank();
        auditService.log(admin, reveal ? "VIEW_USER_PII" : "VIEW_USER", "USER", userId,
                reveal ? revealReason : null, null, null);

        List<SanctionRow> sanctions = sanctionRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> new SanctionRow(s.getId(), s.getType(), s.getReason(), s.getStartsAt(),
                        s.getEndsAt(), s.getReleasedAt(), s.getAdminId()))
                .toList();

        var devices = deviceRepository.findAllByUserIdOrderByLastSeenAtDesc(userId);
        Instant lastSeenAt = devices.stream().map(UserDevice::getLastSeenAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);

        // 잔액·구독은 결제 열람 권한이 있는 관리자에게만 — 보기 전용(VIEWER)에게는 비워 보낸다.
        boolean payments = admin.role().canViewPayments();
        AdminWalletSummary wallet = payments
                ? walletRepository.findByUserId(userId)
                        .map(w -> new AdminWalletSummary(w.getBookmarkBalance(), w.getPostcardBalance(), w.getStampBalance()))
                        .orElse(new AdminWalletSummary(0, 0, 0))
                : null;
        AdminSubscriptionRow subscription = payments
                ? subscriptionRepository.findTopByUserIdOrderByIdDesc(userId).map(AdminUserService::toSubscriptionRow).orElse(null)
                : null;

        return new UserDetailView(
                user.getId(), user.getHandle(), user.getNickname(),
                reveal ? user.getEmail() : PrivacyMasker.email(user.getEmail()),
                user.getStatus(), user.getCreatedAt(),
                sessionRepository.countByUserId(userId),
                sessionRepository.sumDurationSecByUserSince(userId, Instant.EPOCH),
                reviewRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, "VISIBLE",
                        PageRequest.of(0, 1)).getTotalElements(),
                clubMemberRepository.findAllByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE).size(),
                sanctions,
                user.getDeletionRequestedAt(),
                user.getEmailVerifiedAt(),
                user.getIdentityVerifiedAt(),
                user.getPasswordHash() != null,
                lastSeenAt,
                wallet,
                subscription,
                devices.stream().map(d -> new AdminDeviceRow(d.getPlatform(), d.isPushEnabled(),
                        tail(d.getPushToken()), d.getLastSeenAt(), d.getCreatedAt())).toList(),
                identityRepository.findAllByUserId(userId).stream()
                        .map(i -> new AdminIdentityRow(i.getProvider(), i.getCreatedAt())).toList(),
                latestConsents(userId));
    }

    /** 이 회원의 로그인을 모두 끊는다 — 기기 분실·계정 도용 신고 대응. 다음 요청부터 401, 다시 로그인해야 한다. */
    @Transactional
    public void revokeSessions(AuthAdmin admin, Long userId, String reason) {
        if (!admin.canSanction()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        userRepository.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Instant now = clock.instant();
        refreshTokenRepository.revokeAllByUserId(userId, now);
        accessRevocations.revoke(userId, now);
        auditService.log(admin, "REVOKE_USER_SESSIONS", "USER", userId, reason, null, null);
    }

    static AdminSubscriptionRow toSubscriptionRow(Subscription s) {
        return new AdminSubscriptionRow(s.getId(), s.getStore(), s.getProductId(), s.getStatus(),
                s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getCreatedAt());
    }

    /** 동의는 바꿀 때마다 한 줄씩 쌓인다 — 종류별 마지막 줄이 지금 상태다. */
    private List<AdminConsentRow> latestConsents(Long userId) {
        Map<ConsentKind, UserConsent> latest = new EnumMap<>(ConsentKind.class);
        for (UserConsent consent : consentRepository.findAllByUserIdOrderByIdAsc(userId)) {
            latest.put(consent.getKind(), consent);
        }
        return latest.values().stream()
                .map(c -> new AdminConsentRow(c.getKind(), c.isAgreed(), c.getVersion(), c.getCreatedAt()))
                .toList();
    }

    private static String tail(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.length() <= 6 ? token : token.substring(token.length() - 6);
    }

    /**
     * 제재를 건다. 상태는 살아 있는 제재 중 가장 무거운 것이 되며(새 제재가 기존 상태를 낮추지 않는다),
     * 사유와 기간은 회원에게 알림으로 전달된다(약관 제10조 ②).
     * 정지·영구정지는 남아 있는 로그인 토큰을 바로 끊고, 영구정지는 참가한 모임에서 나가게 하고 푸시를 끈다.
     */
    @Transactional
    public void sanction(AuthAdmin admin, Long userId, SanctionRequest request) {
        boolean allowed = request.type() == SanctionType.WARN
                ? admin.role().canWarn()
                : admin.role().canSanction();
        if (!allowed) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        requireNotWithdrawn(user);

        Instant now = clock.instant();
        UserStatus before = user.getStatus();
        Instant endsAt = request.durationDays() == null
                ? null
                : now.plus(request.durationDays(), ChronoUnit.DAYS);

        UserSanction saved = sanctionRepository.save(new UserSanction(
                userId, admin.id(), request.type(), request.reason().trim(), endsAt));

        UserStatus after = SanctionPolicy.heavier(before, SanctionPolicy.statusOf(request.type()));
        user.changeStatus(after);
        applySideEffects(user, before, after, now);
        notifySanction(user, request.type(), saved.getReason(), endsAt);

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("status", after.name());
        afterData.put("sanctionId", saved.getId());
        afterData.put("endsAt", endsAt == null ? null : endsAt.toString());
        auditService.log(admin, "SANCTION_" + request.type().name(), "USER", userId,
                request.reason(), Map.of("status", before.name()), afterData);
    }

    /** 지갑 수동 조정 — IAP·제휴 적립 전의 베타 운영 경로 (§14.2). */
    @Transactional
    public void adjustWallet(AuthAdmin admin, Long userId, WalletAdjustRequest request) {
        if (!admin.canSanction()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Map<String, Object> before = balances(userId);
        walletService.adminAdjust(userId, request.bookmarks(), request.postcards(), request.stamps());
        Map<String, Object> after = balances(userId);
        after.put("bookmarkDelta", request.bookmarks());
        after.put("postcardDelta", request.postcards());
        after.put("stampDelta", request.stamps());
        auditService.log(admin, "ADJUST_WALLET", "USER", userId, request.reason(), before, after);
    }

    /** 구독 수동 지급 — 스토어 IAP 검증 전의 운영 경로 (§14.2). */
    @Transactional
    public void grantSubscription(AuthAdmin admin, Long userId, SubscriptionGrantRequest request) {
        if (!admin.canSanction()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        subscriptionService.adminGrant(userId, request.months());
        auditService.log(admin, "GRANT_SUBSCRIPTION", "USER", userId, request.reason(),
                Map.of(), Map.of("months", String.valueOf(request.months())));
    }

    @Transactional
    public void revokeSubscription(AuthAdmin admin, Long userId, String reason) {
        if (!admin.canSanction()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        subscriptionService.adminRevoke(userId);
        auditService.log(admin, "REVOKE_SUBSCRIPTION", "USER", userId, reason, Map.of(), Map.of());
    }

    /** 제재를 푼다. 남은 제재로 상태를 다시 계산하므로 다른 제재가 살아 있으면 그 상태가 남는다. */
    @Transactional
    public void releaseSanction(AuthAdmin admin, Long userId, Long sanctionId, String reason) {
        if (!admin.canSanction()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        UserSanction sanction = sanctionRepository.findById(sanctionId)
                .filter(s -> s.belongsTo(userId))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (sanction.isReleased()) {
            throw ApiException.of(ErrorCode.ADMIN_SANCTION_ALREADY_RELEASED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        requireNotWithdrawn(user);

        UserStatus before = user.getStatus();
        sanction.release();
        UserStatus after = recompute(user, clock.instant());

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("status", before.name());
        beforeData.put("sanctionId", sanctionId);
        beforeData.put("type", sanction.getType().name());
        auditService.log(admin, "RELEASE_SANCTION", "USER", userId, reason,
                beforeData, Map.of("status", after.name()));
    }

    /**
     * 기간이 끝난 제재를 반영해 상태를 다시 맞춘다({@code SanctionExpiryJob}). 제재 기록이 하나도 없는 회원은
     * 근거 없이 상태를 바꾸지 않도록 건드리지 않는다. 바뀌었으면 true.
     */
    @Transactional
    public boolean reconcileStatus(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getDeletionRequestedAt() != null) {
            return false;
        }
        if (sanctionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).isEmpty()) {
            return false;
        }
        UserStatus before = user.getStatus();
        UserStatus after = recompute(user, clock.instant());
        if (before != after) {
            log.info("Sanction status reconciled: userId={} {} -> {}", userId, before, after);
            return true;
        }
        return false;
    }

    /** 살아 있는 제재로 상태를 정하고 저장한다. 영구정지가 풀릴 때는 그 사이 겹친 닉네임을 비켜 준다. */
    private UserStatus recompute(User user, Instant now) {
        UserStatus before = user.getStatus();
        UserStatus after = SanctionPolicy.statusFor(
                sanctionRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()), now);
        if (before == after) {
            return after;
        }
        if (before == UserStatus.TERMINATED) {
            avoidNicknameClash(user);
        }
        user.changeStatus(after);
        if (!after.canLogin()) {
            applySideEffects(user, before, after, now);
        } else if (!before.canLogin()) {
            accessRevocations.clear(user.getId());
        }
        return after;
    }

    /**
     * 영구정지 회원의 닉네임은 유일 인덱스에서 빠지므로(V48) 그 사이 다른 사람이 같은 닉네임을 가졌을 수 있다.
     * 그대로 풀면 인덱스 위반으로 실패하니 '_회원번호' 를 붙인다.
     */
    private void avoidNicknameClash(User user) {
        String nickname = user.getNickname();
        if (nickname != null && userRepository.existsByNicknameIgnoreCaseAndIdNot(nickname.trim(), user.getId())) {
            String base = nickname.trim();
            if (base.length() > NICKNAME_BASE_MAX) {
                base = base.substring(0, NICKNAME_BASE_MAX);
            }
            user.updateProfile(base + "_" + user.getId(), null);
        }
    }

    /** 로그인을 막는 상태로 바뀌면 토큰을 끊고, 영구정지는 모임과 푸시까지 정리한다. */
    private void applySideEffects(User user, UserStatus before, UserStatus after, Instant now) {
        if (after.canLogin() || before == after) {
            return;
        }
        refreshTokenRepository.revokeAllByUserId(user.getId(), now);
        accessRevocations.revoke(user.getId(), now);
        if (after == UserStatus.TERMINATED) {
            clubService.leaveAllOnWithdrawal(user.getId());
            for (UserDevice device : deviceRepository.findAllByUserIdAndPushEnabledTrue(user.getId())) {
                device.disablePush();
            }
        }
    }

    private void notifySanction(User user, SanctionType type, String reason, Instant endsAt) {
        String title = switch (type) {
            case WARN -> "운영 정책 위반으로 경고를 받았어요";
            case WRITE_BAN -> "글쓰기가 제한됐어요";
            case SUSPEND -> "계정 이용이 정지됐어요";
            case TERMINATE -> "계정 이용이 영구 정지됐어요";
        };
        StringBuilder body = new StringBuilder("사유: ").append(reason);
        if (type != SanctionType.WARN) {
            body.append("\n기간: ").append(endsAt == null ? "해제될 때까지" : NOTICE_DATE.format(endsAt) + "까지");
        }
        body.append("\n이의가 있으면 설정 > 고객문의로 알려 주세요.");
        String text = body.length() > 500 ? body.substring(0, 500) : body.toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sanctionType", type.name());
        payload.put("endsAt", endsAt == null ? null : endsAt.toString());
        notificationService.notice(new NotificationRequest(
                user.getId(), NotificationType.SANCTION_NOTICE, null, null, null,
                title, text, payload, null));
    }

    /** 탈퇴를 신청한 회원은 30일 뒤 지워질 계정이다 — 상태를 바꾸면 숨겨진 글이 다시 보이고 삭제 잡이 건너뛴다. */
    private static void requireNotWithdrawn(User user) {
        if (user.getDeletionRequestedAt() != null) {
            throw ApiException.of(ErrorCode.ADMIN_USER_WITHDRAWN);
        }
    }

    /** 감사 로그용 잔액 — 지갑을 만들거나 월간 지급을 돌리지 않도록 저장소에서 바로 읽는다. */
    private Map<String, Object> balances(Long userId) {
        Map<String, Object> map = new LinkedHashMap<>();
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        map.put("bookmarks", wallet == null ? 0 : wallet.getBookmarkBalance());
        map.put("postcards", wallet == null ? 0 : wallet.getPostcardBalance());
        map.put("stamps", wallet == null ? 0 : wallet.getStampBalance());
        return map;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
