package app.bookey.api.notification;

import app.bookey.api.notification.dto.NotificationDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.OpsFlag;
import app.bookey.domain.admin.OpsFlagRepository;
import app.bookey.domain.notification.Notification;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.NotifyTone;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Map;
import java.util.Optional;

/**
 * 알림 발송 정책 (§F5, §8.4, §12.4).
 *
 * <ul>
 *   <li>개인 알림: 일 2건 / 주 7건 상한</li>
 *   <li>모임 알림: 모임당 일 1건, 전체 일 3건 — 개인 한도와 <b>별도</b></li>
 *   <li>조용 시간(기본 22:00~08:00)에는 다음 발송 가능 시각으로 미룬다</li>
 *   <li>무음 모드 사용자는 푸시를 만들지 않는다 (인앱 배지로만 노출)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OpsFlagRepository opsFlagRepository;
    private final BookeyProperties properties;

    public record NotificationRequest(
            Long userId,
            NotificationType type,
            Short lagLevel,
            Long readingRecordId,
            Long clubId,
            String title,
            String body,
            Map<String, Object> payload,
            String experimentVariant
    ) {}

    /**
     * 정책을 통과하면 알림을 예약한다. 한도 초과·무음 모드면 비어 있는 Optional 을 돌려준다.
     * 예약만 하고 실제 발송은 디스패처가 맡는다.
     */
    @Transactional
    public Optional<Notification> schedule(NotificationRequest request) {
        User user = userRepository.findById(request.userId()).orElse(null);
        if (user == null || !user.getStatus().canLogin()) {
            return Optional.empty();
        }
        if (user.getNotifyTone() == NotifyTone.SILENT && !request.type().bypassesCap()) {
            return Optional.empty();
        }
        if (!isPushEnabledGlobally()) {
            return Optional.empty();
        }

        ZoneId zone = zoneOf(user);
        ZonedDateTime now = ZonedDateTime.now(zone);

        if (!request.type().bypassesCap() && exceedsCap(user, request, now)) {
            log.debug("Notification capped: user={} type={}", user.getId(), request.type());
            return Optional.empty();
        }

        Instant scheduledAt = resolveSendTime(user, now, zone);

        Notification notification = Notification.builder()
                .userId(user.getId())
                .type(request.type())
                .lagLevel(request.lagLevel())
                .readingRecordId(request.readingRecordId())
                .clubId(request.clubId())
                .title(request.title())
                .body(request.body())
                .payload(request.payload())
                .scheduledAt(scheduledAt)
                .experimentVariant(request.experimentVariant())
                .build();
        return Optional.of(notificationRepository.save(notification));
    }

    private boolean isPushEnabledGlobally() {
        return opsFlagRepository.findById(OpsFlag.PUSH_ENABLED)
                .map(OpsFlag::isEnabled)
                .orElse(true);
    }

    private boolean exceedsCap(User user, NotificationRequest request, ZonedDateTime now) {
        Instant dayStart = now.toLocalDate().atStartOfDay(now.getZone()).toInstant();
        Instant dayEnd = dayStart.plus(Duration.ofDays(1));

        if (request.clubId() != null) {
            long clubTotal = notificationRepository.countClubScheduled(user.getId(), dayStart, dayEnd);
            if (clubTotal >= Math.min(user.getClubNotifyCap(), properties.notification().clubDailyCap())) {
                return true;
            }
            long perClub = notificationRepository.countClubScheduledForClub(
                    user.getId(), request.clubId(), dayStart, dayEnd);
            return perClub >= 1;   // 모임당 하루 1건
        }

        long personal = notificationRepository.countPersonalScheduled(user.getId(), dayStart, dayEnd);
        if (personal >= Math.min(user.getDailyNotifyCap(), properties.notification().dailyCap())) {
            return true;
        }
        Instant weekStart = now.minusDays(6).toLocalDate().atStartOfDay(now.getZone()).toInstant();
        long weekly = notificationRepository.countPersonalScheduled(user.getId(), weekStart, dayEnd);
        return weekly >= properties.notification().weeklyCap();
    }

    /** 조용 시간이면 종료 시각으로, 아니면 즉시 발송. */
    private Instant resolveSendTime(User user, ZonedDateTime now, ZoneId zone) {
        if (!user.isQuietHour(now.getHour())) {
            return now.toInstant();
        }
        ZonedDateTime candidate = now.withHour(user.getQuietHoursEnd())
                .withMinute(0).withSecond(0).withNano(0);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (Exception e) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(Long userId, Pageable pageable) {
        return PageResponse.of(
                notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable),
                this::toView);
    }

    @Transactional
    public void markOpened(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!notification.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        notification.markOpened();
    }

    @Transactional
    public void updateSettings(Long userId, NotificationSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        user.updateNotificationSettings(request.notifyTone(), request.quietHoursStart(),
                request.quietHoursEnd(), request.dailyNotifyCap(), request.clubNotifyCap(),
                request.allowNudge());
    }

    public NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getLagLevel(), n.getReadingRecordId(),
                n.getClubId(), n.getTitle(), n.getBody(), n.getPayload(),
                n.getScheduledAt(), n.getSentAt(), n.getOpenedAt());
    }
}
