package app.bookey.api.notification;

import app.bookey.domain.notification.Notification;
import app.bookey.domain.user.UserDevice;
import app.bookey.domain.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 푸시 발송 어댑터.
 *
 * <p>FCM/APNs 연동 자리다. 현재는 등록된 디바이스에 대해 발송 로그만 남긴다 —
 * 자격 증명(서비스 계정 키·APNs 인증서)이 준비되면 이 클래스만 교체하면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushSender {

    private final UserDeviceRepository deviceRepository;

    /** 발송 성공 여부. 디바이스가 없으면(푸시 거부) false — 인앱 배너로만 노출된다(§8.4). */
    public boolean send(Notification notification) {
        List<UserDevice> devices =
                deviceRepository.findAllByUserIdAndPushEnabledTrue(notification.getUserId());
        if (devices.isEmpty()) {
            log.debug("No push device for user {} — in-app only", notification.getUserId());
            return false;
        }
        for (UserDevice device : devices) {
            log.info("PUSH[{}] user={} type={} title={}",
                    device.getPlatform(), notification.getUserId(),
                    notification.getType(), notification.getTitle());
        }
        return true;
    }
}
