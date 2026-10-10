package app.bookey.api.push;

import app.bookey.api.notification.PushSender;
import app.bookey.domain.notification.Notification;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.notification.SendTimeResolver;
import app.bookey.domain.push.PushCampaign;
import app.bookey.domain.push.PushCampaignRepository;
import app.bookey.domain.push.PushCampaignStatus;
import app.bookey.domain.user.NotifyTone;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 캠페인 발송 단계 — 잡({@code PushCampaignJob})이 차례로 부른다. 단계마다 트랜잭션이 따로라 한 단계가 실패해도 다음 실행에서 이어진다.
 *  1. 시작: 예약 시각이 된 캠페인을 '보내는 중' 으로 가져간다(한 서버만 성공).
 *  2. 펼치기: 대상자를 회원 id 순으로 한 묶음씩 알림 행으로 만든다. 방해 금지·광고 야간이면 그 끝으로 미루고,
 *     무음(SILENT) 회원은 푸시 없이 앱 목록에만 남긴다.
 *  3. 보내기: 때가 된 캠페인 알림을 잠그며 가져와 Expo 에 100건씩 묶어 보낸다.
 *  4. 끝내기: 다 펼쳤고 남은 알림이 없으면 DONE.
 */
@Service
@RequiredArgsConstructor
public class PushCampaignRunner {

    static final int AUDIENCE_CHUNK = 500;
    static final int DELIVERY_BATCH = 500;

    private final PushCampaignRepository campaignRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushSender pushSender;
    private final Clock clock;

    @Transactional
    public List<Long> startDue() {
        Instant now = clock.instant();
        List<Long> started = new ArrayList<>();
        for (PushCampaign campaign : campaignRepository
                .findAllByStatusAndScheduledAtLessThanEqualOrderByIdAsc(PushCampaignStatus.SCHEDULED, now)) {
            if (campaignRepository.claim(campaign.getId(), now) == 1) {
                started.add(campaign.getId());
            }
        }
        return started;
    }

    /** 대상자 한 묶음을 알림으로 펼친다. 더 펼칠 대상이 남았으면 true. */
    @Transactional
    public boolean expandNext(Long campaignId) {
        PushCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null || campaign.getStatus() != PushCampaignStatus.SENDING || campaign.isAudienceDone()) {
            return false;
        }
        Instant now = clock.instant();
        List<Long> ids = campaignRepository.findAudience(campaign.getCursorUserId(), campaign.isMarketing(),
                AUDIENCE_CHUNK);

        String title = PushMessages.title(campaign.getKind(), campaign.getTitle());
        String body = PushMessages.body(campaign.getKind(), campaign.getBody());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("campaignId", campaign.getId());
        if (campaign.getLinkUrl() != null) {
            payload.put("link", campaign.getLinkUrl());
        }

        List<Notification> rows = new ArrayList<>();
        for (User user : userRepository.findAllById(ids)) {
            Instant sendAt = SendTimeResolver.campaignSendTime(now, SendTimeResolver.zoneOf(user.getTimezone()),
                    user.getQuietHoursStart(), user.getQuietHoursEnd(), campaign.isMarketing());
            Notification notification = Notification.builder()
                    .userId(user.getId())
                    .type(NotificationType.ANNOUNCEMENT)
                    .title(title)
                    .body(body)
                    .payload(payload)
                    .scheduledAt(sendAt)
                    .campaignId(campaign.getId())
                    .build();
            if (user.getNotifyTone() == NotifyTone.SILENT) {
                notification.markSent();   // 무음 — 푸시 없이 앱 알림 목록에만 남긴다
            }
            rows.add(notification);
        }
        notificationRepository.saveAll(rows);

        boolean last = ids.size() < AUDIENCE_CHUNK;
        campaign.advance(ids.isEmpty() ? campaign.getCursorUserId() : ids.getLast(), rows.size(), last);
        return !last;
    }

    /** 때가 된 캠페인 알림을 보낸다. 보낸 수를 돌려준다. */
    @Transactional
    public int deliverDue() {
        List<Notification> due = notificationRepository.lockDueCampaignNotifications(clock.instant(), DELIVERY_BATCH);
        if (due.isEmpty()) {
            return 0;
        }
        pushSender.sendBatch(due);
        due.forEach(Notification::markSent);
        return due.size();
    }

    @Transactional
    public void finishIfDone(Long campaignId) {
        campaignRepository.findById(campaignId)
                .filter(c -> c.getStatus() == PushCampaignStatus.SENDING && c.isAudienceDone())
                .filter(c -> notificationRepository.countByCampaignIdAndSentAtIsNull(c.getId()) == 0)
                .ifPresent(c -> c.finish(clock.instant()));
    }
}
