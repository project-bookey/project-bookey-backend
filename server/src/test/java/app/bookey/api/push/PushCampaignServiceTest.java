package app.bookey.api.push;

import app.bookey.admin.support.AdminAuditService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.push.dto.PushCampaignDtos.PushCampaignRequest;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.push.PushCampaign;
import app.bookey.domain.push.PushCampaignKind;
import app.bookey.domain.push.PushCampaignRepository;
import app.bookey.domain.push.PushCampaignStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushCampaignServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");
    private static final AuthAdmin SUPER = new AuthAdmin(1L, "root@bookey.app", AdminRole.SUPER_ADMIN);
    private static final AuthAdmin OPERATOR = new AuthAdmin(2L, "op@bookey.app", AdminRole.OPERATOR);

    private final PushCampaignRepository campaignRepository = mock(PushCampaignRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PushCampaignService service = new PushCampaignService(campaignRepository, notificationRepository,
            mock(NotificationService.class), mock(AdminRepository.class), mock(AdminAuditService.class),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static PushCampaignRequest request(String link, Instant at) {
        return new PushCampaignRequest(PushCampaignKind.NOTICE, "점검 안내", "내일 새벽 2시", link, at, "공지");
    }

    @Test
    @DisplayName("전체 푸시는 최고 관리자만 — 운영자는 못 한다")
    void onlySuper() {
        assertThatThrownBy(() -> service.create(OPERATOR, request(null, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("링크는 https 주소나 앱 화면 경로만, 지난 시각 예약은 막는다")
    void validation() {
        assertThatThrownBy(() -> service.create(SUPER, request("javascript:alert(1)", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.create(SUPER, request("//evil.example", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.create(SUPER, request(null, NOW.minusSeconds(3600))))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(PushCampaignService.link("/book/1")).isEqualTo("/book/1");
    }

    @Test
    @DisplayName("보내기 시작한 캠페인은 고칠 수 없고, 취소하면 아직 안 나간 알림을 지운다")
    void editAndCancel() {
        PushCampaign campaign = new PushCampaign(PushCampaignKind.NOTICE, "점검", "본문", null, NOW, 1L);
        campaign.start(NOW);
        when(campaignRepository.findById(5L)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.update(SUPER, 5L, request(null, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        service.cancel(SUPER, 5L, "문구 실수");
        assertThat(campaign.getStatus()).isEqualTo(PushCampaignStatus.CANCELLED);
        verify(notificationRepository).deleteUnsentByCampaign(5L);
    }

    @Test
    @DisplayName("바로 보내기(예약 시각 비움)는 지금 시각으로 예약한다")
    void sendNow() {
        when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var row = service.create(SUPER, request(null, null));
        assertThat(row.scheduledAt()).isEqualTo(NOW);
        assertThat(row.status()).isEqualTo(PushCampaignStatus.SCHEDULED);
    }
}
