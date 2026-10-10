package app.bookey.batch;

import app.bookey.api.notification.PushSender;
import app.bookey.domain.admin.OpsFlag;
import app.bookey.domain.admin.OpsFlagRepository;
import app.bookey.domain.notification.Notification;
import app.bookey.domain.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 예약된 알림 발송 (§F5). 발송·오픈·전환을 전부 로깅해 A/B 테스트에 쓴다.
 * 푸시 킬스위치(PUSH_ENABLED)가 꺼져 있으면 푸시 없이 인앱 목록에만 남긴다 — 이미 쌓인 알림이
 * 스위치를 다시 켜는 순간 한꺼번에 나가지 않게, 발송 처리는 그대로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchJob {

    private static final int BATCH_SIZE = 200;

    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;
    private final OpsFlagRepository opsFlagRepository;

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 30 * 1000)
    @Transactional
    public void dispatch() {
        List<Notification> due = notificationRepository
                .findDueForSend(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        boolean pushEnabled = opsFlagRepository.findById(OpsFlag.PUSH_ENABLED)
                .map(OpsFlag::isEnabled)
                .orElse(true);
        for (Notification notification : due) {
            if (pushEnabled) {
                pushSender.send(notification);
            }
            notification.markSent();   // 푸시 거부 사용자·킬스위치 중에도 인앱 목록에는 남는다
        }
        if (!due.isEmpty()) {
            log.info("NotificationDispatchJob: {} notifications dispatched (push {})",
                    due.size(), pushEnabled ? "on" : "off");
        }
    }
}
