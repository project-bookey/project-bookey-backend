package app.bookey.admin;

import app.bookey.admin.dto.AdminDtos.SanctionRequest;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.api.club.ClubService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.notification.NotificationService.NotificationRequest;
import app.bookey.api.social.SubscriptionService;
import app.bookey.api.social.WalletService;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.security.UserAccessRevocations;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.admin.SanctionType;
import app.bookey.domain.admin.UserSanction;
import app.bookey.domain.admin.UserSanctionRepository;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.DevicePlatform;
import app.bookey.domain.user.RefreshTokenRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserDevice;
import app.bookey.domain.user.UserDeviceRepository;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import app.bookey.domain.wallet.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");
    private static final AuthAdmin OPERATOR = new AuthAdmin(1L, "op@bookey.app", AdminRole.OPERATOR);
    private static final AuthAdmin SUPPORT = new AuthAdmin(2L, "cs@bookey.app", AdminRole.SUPPORT);
    private static final AuthAdmin VIEWER = new AuthAdmin(3L, "view@bookey.app", AdminRole.VIEWER);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSanctionRepository sanctionRepository = mock(UserSanctionRepository.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserDeviceRepository deviceRepository = mock(UserDeviceRepository.class);
    private final UserAccessRevocations revocations = mock(UserAccessRevocations.class);
    private final ClubService clubService = mock(ClubService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AdminUserService service = new AdminUserService(
            userRepository, mock(ReadingRecordRepository.class), mock(ReadingSessionRepository.class),
            mock(ReviewRepository.class), mock(ClubMemberRepository.class), sanctionRepository, auditService,
            mock(SubscriptionService.class), mock(WalletService.class), mock(WalletRepository.class),
            refreshTokenRepository, deviceRepository, revocations, clubService, notificationService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    /** 저장된 제재 — 서비스가 save 한 것을 그대로 다시 읽어 오도록 흉내 낸다. */
    private final List<UserSanction> stored = new ArrayList<>();

    private User user(long id, UserStatus status) {
        User user = User.builder().handle("reader" + id).email("reader@dev.local").nickname("독자").build();
        set(user, "id", id);
        user.changeStatus(status);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        doAnswer(inv -> {
            UserSanction s = inv.getArgument(0);
            set(s, "id", (long) stored.size() + 100);
            stored.add(s);
            return s;
        }).when(sanctionRepository).save(any());
        when(sanctionRepository.findAllByUserIdOrderByCreatedAtDesc(id)).thenAnswer(inv -> List.copyOf(stored));
        return user;
    }

    private UserSanction existing(long userId, SanctionType type, Instant endsAt) {
        UserSanction s = new UserSanction(userId, 1L, type, "기존 사유", endsAt);
        set(s, "id", (long) stored.size() + 100);
        stored.add(s);
        when(sanctionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private static void set(Object target, String field, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException(field);
    }

    @Test
    @DisplayName("CS(SUPPORT)는 경고를 줄 수 있고, 경고는 상태를 바꾸지 않은 채 사유를 알림으로 보낸다")
    void supportCanWarn() {
        User user = user(10L, UserStatus.ACTIVE);

        service.sanction(SUPPORT, 10L, new SanctionRequest(SanctionType.WARN, " 욕설 ", null));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).notice(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.SANCTION_NOTICE);
        assertThat(sent.getValue().body()).contains("사유: 욕설").doesNotContain("기간");
        verify(auditService).log(eq(SUPPORT), eq("SANCTION_WARN"), eq("USER"), eq(10L), eq(" 욕설 "), anyMap(), anyMap());
        verify(revocations, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("보기 전용(VIEWER)은 경고도 못 하고, CS 는 경고를 넘는 제재를 못 한다")
    void roleChecks() {
        user(10L, UserStatus.ACTIVE);
        assertThatThrownBy(() -> service.sanction(VIEWER, 10L, new SanctionRequest(SanctionType.WARN, "사유", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        assertThatThrownBy(() -> service.sanction(SUPPORT, 10L, new SanctionRequest(SanctionType.WRITE_BAN, "사유", 7)))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("탈퇴를 신청한 회원은 제재할 수 없다 — 상태를 바꾸면 숨긴 글이 살아나고 삭제 잡이 건너뛴다")
    void withdrawnRejected() {
        User user = user(10L, UserStatus.TERMINATED);
        set(user, "deletionRequestedAt", NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.sanction(OPERATOR, 10L, new SanctionRequest(SanctionType.WRITE_BAN, "사유", 7)))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_USER_WITHDRAWN);
        assertThat(user.getStatus()).isEqualTo(UserStatus.TERMINATED);
    }

    @Test
    @DisplayName("정지된 회원에게 쓰기정지를 더 걸어도 정지가 풀리지 않는다")
    void lighterSanctionDoesNotDowngrade() {
        User user = user(10L, UserStatus.SUSPENDED);

        service.sanction(OPERATOR, 10L, new SanctionRequest(SanctionType.WRITE_BAN, "사유", 7));

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("정지는 남은 로그인 토큰을 바로 끊고, 기간을 알림에 적는다")
    void suspendRevokesTokens() {
        User user = user(10L, UserStatus.ACTIVE);

        service.sanction(OPERATOR, 10L, new SanctionRequest(SanctionType.SUSPEND, "도배", 3));

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(refreshTokenRepository).revokeAllByUserId(10L, NOW);
        verify(revocations).revoke(10L, NOW);
        verify(clubService, never()).leaveAllOnWithdrawal(any());
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).notice(sent.capture());
        assertThat(sent.getValue().body()).contains("기간: 2026.10.13 12:00까지");
    }

    @Test
    @DisplayName("영구정지는 모임에서 나가게 하고 기기 푸시를 끈다")
    void terminateLeavesClubsAndMutesPush() {
        user(10L, UserStatus.ACTIVE);
        UserDevice device = new UserDevice(10L, DevicePlatform.IOS, "ExponentPushToken[x]");
        when(deviceRepository.findAllByUserIdAndPushEnabledTrue(10L)).thenReturn(List.of(device));

        service.sanction(OPERATOR, 10L, new SanctionRequest(SanctionType.TERMINATE, "사기", null));

        verify(clubService).leaveAllOnWithdrawal(10L);
        assertThat(device.isPushEnabled()).isFalse();
    }

    @Test
    @DisplayName("쓰기정지를 풀면, 기간 없는 경고가 남아 있어도 ACTIVE 로 돌아온다")
    void releaseWithWarnReturnsActive() {
        User user = user(10L, UserStatus.WRITE_BANNED);
        existing(10L, SanctionType.WARN, null);
        UserSanction ban = existing(10L, SanctionType.WRITE_BAN, null);

        service.releaseSanction(OPERATOR, 10L, ban.getId(), "오해 소명");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(ban.isReleased()).isTrue();
        verify(revocations, never()).clear(any());
    }

    @Test
    @DisplayName("정지를 풀어도 다른 정지가 살아 있으면 정지가 남는다")
    void releaseKeepsOtherActiveSanction() {
        User user = user(10L, UserStatus.SUSPENDED);
        existing(10L, SanctionType.SUSPEND, NOW.plusSeconds(3600));
        UserSanction second = existing(10L, SanctionType.SUSPEND, null);

        service.releaseSanction(OPERATOR, 10L, second.getId(), "사유");

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("다른 회원의 제재 번호로는 풀 수 없고, 이미 푼 제재는 다시 풀 수 없다")
    void releaseChecksOwnershipAndState() {
        user(10L, UserStatus.WRITE_BANNED);
        UserSanction others = new UserSanction(99L, 1L, SanctionType.WRITE_BAN, "남의 제재", null);
        set(others, "id", 500L);
        when(sanctionRepository.findById(500L)).thenReturn(Optional.of(others));
        assertThatThrownBy(() -> service.releaseSanction(OPERATOR, 10L, 500L, "사유"))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        UserSanction done = existing(10L, SanctionType.WRITE_BAN, null);
        done.release();
        assertThatThrownBy(() -> service.releaseSanction(OPERATOR, 10L, done.getId(), "사유"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_SANCTION_ALREADY_RELEASED);
    }

    @Test
    @DisplayName("영구정지를 풀 때 그사이 같은 닉네임을 쓰는 사람이 있으면 '_회원번호' 를 붙이고, 로그인 차단 기록을 지운다")
    void releaseTerminateAvoidsNicknameClash() {
        User user = user(10L, UserStatus.TERMINATED);
        UserSanction terminate = existing(10L, SanctionType.TERMINATE, null);
        when(userRepository.existsByNicknameIgnoreCaseAndIdNot("독자", 10L)).thenReturn(true);

        service.releaseSanction(OPERATOR, 10L, terminate.getId(), "복구");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getNickname()).isEqualTo("독자_10");
        verify(revocations).clear(10L);
    }

    @Test
    @DisplayName("reconcileStatus — 기간이 끝난 쓰기정지는 풀고, 제재 기록이 없는 회원은 건드리지 않는다")
    void reconcile() {
        User banned = user(10L, UserStatus.WRITE_BANNED);
        existing(10L, SanctionType.WRITE_BAN, NOW.minusSeconds(1));
        assertThat(service.reconcileStatus(10L)).isTrue();
        assertThat(banned.getStatus()).isEqualTo(UserStatus.ACTIVE);

        stored.clear();
        User legacy = user(11L, UserStatus.SUSPENDED);
        when(sanctionRepository.findAllByUserIdOrderByCreatedAtDesc(11L)).thenReturn(List.of());
        assertThat(service.reconcileStatus(11L)).isFalse();
        assertThat(legacy.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }
}
