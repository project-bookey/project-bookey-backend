package app.bookey.api.notification;

import app.bookey.domain.notification.Notification;
import app.bookey.domain.notification.ExpoPushTicket;
import app.bookey.domain.notification.ExpoPushTicketRepository;
import app.bookey.domain.user.UserDevice;
import app.bookey.domain.user.UserDeviceRepository;
import app.bookey.common.config.BookeyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * 푸시 발송 어댑터.
 *
 * <p>Expo Push Service가 Android는 FCM, iOS는 APNs로 전달한다.
 * 앱에는 Expo Push Token만 저장하므로 플랫폼별 서버 SDK를 중복 운영하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushSender {

    private final UserDeviceRepository deviceRepository;
    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;
    private final ExpoPushTicketRepository ticketRepository;

    /** 발송 성공 여부. 디바이스가 없으면(푸시 거부) false — 인앱 배너로만 노출된다(§8.4). */
    @Transactional
    public boolean send(Notification notification) {
        List<UserDevice> devices =
                deviceRepository.findAllByUserIdAndPushEnabledTrue(notification.getUserId());
        if (devices.isEmpty()) {
            log.debug("No push device for user {} — in-app only", notification.getUserId());
            return false;
        }
        boolean sent = false;
        for (UserDevice device : devices) {
            Map<String, Object> data = new HashMap<>(notification.getPayload());
            data.put("notificationId", notification.getId());
            data.put("type", notification.getType().name());
            if (notification.getClubId() != null) data.put("clubId", notification.getClubId());
            if (notification.getReadingRecordId() != null) data.put("readingRecordId", notification.getReadingRecordId());

            Map<String, Object> message = new HashMap<>();
            message.put("to", device.getPushToken());
            message.put("title", notification.getTitle());
            message.put("body", notification.getBody());
            message.put("sound", "default");
            message.put("channelId", "default");
            message.put("data", data);
            try {
                RestClient.RequestBodySpec request = bookApiRestClient.post()
                        .uri(properties.notification().expoPushUrl())
                        .contentType(MediaType.APPLICATION_JSON);
                if (!properties.notification().expoAccessToken().isBlank()) {
                    request.header("Authorization", "Bearer " + properties.notification().expoAccessToken());
                }
                Map<?, ?> response = request.body(message).retrieve().body(Map.class);
                Map<?, ?> ticket = response != null && response.get("data") instanceof Map<?, ?> value ? value : null;
                if (ticket != null && "ok".equals(ticket.get("status"))) {
                    sent = true;
                    Object ticketId = ticket.get("id");
                    if (ticketId instanceof String id && !id.isBlank()) {
                        ticketRepository.save(new ExpoPushTicket(
                                id, device.getId(), notification.getId(), Instant.now().plusSeconds(90)));
                    }
                } else {
                    Object error = ticket == null ? null : ticket.get("details");
                    if (error instanceof Map<?, ?> details && "DeviceNotRegistered".equals(details.get("error"))) {
                        device.disablePush();
                    }
                    log.warn("Expo push rejected: user={} platform={} ticket={}",
                            notification.getUserId(), device.getPlatform(), ticket);
                }
            } catch (RestClientException e) {
                log.warn("Expo push request failed: user={} platform={}",
                        notification.getUserId(), device.getPlatform(), e);
            }
        }
        return sent;
    }
}
