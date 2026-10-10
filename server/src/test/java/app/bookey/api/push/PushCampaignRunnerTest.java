package app.bookey.api.push;

import app.bookey.api.notification.PushSender;
import app.bookey.domain.notification.Notification;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.push.PushCampaign;
import app.bookey.domain.push.PushCampaignKind;
import app.bookey.domain.push.PushCampaignRepository;
import app.bookey.domain.user.NotifyTone;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushCampaignRunnerTest {

    /** 2026-10-10 22:00 KST — 광고 야간, 기본 방해 금지(22~8시) 안. */
    private static final Instant NIGHT = Instant.parse("2026-10-10T13:00:00Z");

    private final PushCampaignRepository campaignRepository = mock(PushCampaignRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PushCampaignRunner runner = new PushCampaignRunner(campaignRepository, notificationRepository,
            userRepository, mock(PushSender.class), Clock.fixed(NIGHT, ZoneOffset.UTC));

    private static <T> T withId(T entity, long id) {
        try {
            Class<?> type = entity.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return entity;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new IllegalStateException();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("광고 캠페인 펼치기 — (광고) 표시, 다음 날 아침으로 미루고, 무음 회원은 앱 목록에만, 마지막 묶음이면 펼치기 끝")
    void expandMarketing() {
        PushCampaign campaign = withId(new PushCampaign(PushCampaignKind.MARKETING, "가을 이벤트", "책갈피 2배", null,
                NIGHT, 1L), 9L);
        campaign.start(NIGHT);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.findAudience(0L, true, PushCampaignRunner.AUDIENCE_CHUNK)).thenReturn(List.of(3L, 4L));
        User loud = withId(User.builder().handle("a").nickname("가").build(), 3L);
        User silent = withId(User.builder().handle("b").nickname("나").build(), 4L);
        silent.updateNotificationSettings(NotifyTone.SILENT, null, null, null, null, null);
        when(userRepository.findAllById(List.of(3L, 4L))).thenReturn(List.of(loud, silent));

        boolean more = runner.expandNext(9L);

        assertThat(more).isFalse();
        assertThat(campaign.isAudienceDone()).isTrue();
        assertThat(campaign.getTargetCount()).isEqualTo(2);
        assertThat(campaign.getCursorUserId()).isEqualTo(4L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(saved.capture());
        Notification first = saved.getValue().get(0);
        assertThat(first.getTitle()).isEqualTo("(광고) 가을 이벤트");
        assertThat(first.getCampaignId()).isEqualTo(9L);
        assertThat(first.getScheduledAt()).isEqualTo(Instant.parse("2026-10-10T23:00:00Z")); // 다음 날 08:00 KST
        assertThat(first.getSentAt()).isNull();
        assertThat(saved.getValue().get(1).getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("예약 상태가 아니면 펼치지 않는다")
    void notSendingDoesNothing() {
        PushCampaign campaign = withId(new PushCampaign(PushCampaignKind.NOTICE, "공지", "본문", null, NIGHT, 1L), 9L);
        when(campaignRepository.findById(9L)).thenReturn(Optional.of(campaign));
        assertThat(runner.expandNext(9L)).isFalse();
        verify(campaignRepository, org.mockito.Mockito.never()).findAudience(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt());
    }
}
