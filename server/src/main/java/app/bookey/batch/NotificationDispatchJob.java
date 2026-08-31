package app.bookey.batch;

import app.bookey.api.notification.PushSender;
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

/** 예약된 알림 발송 (§F5). 발송·오픈·전환을 전부 로깅해 A/B 테스트에 쓴다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchJob {

    private static final int BATCH_SIZE = 200;

    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 30 * 1000)
    @Transactional
    public void dispatch() {
        List<Notification> due = notificationRepository
                .findDueForSend(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (Notification notification : due) {
            pushSender.send(notification);
            notification.markSent();   // 푸시 거부 사용자도 인앱 목록에는 남는다
        }
        if (!due.isEmpty()) {
            log.info("NotificationDispatchJob: {} notifications dispatched", due.size());
        }
    }
}
