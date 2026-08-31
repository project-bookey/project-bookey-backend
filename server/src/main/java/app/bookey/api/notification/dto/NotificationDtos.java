package app.bookey.api.notification.dto;

import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.NotifyTone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.Map;

public final class NotificationDtos {

    private NotificationDtos() {}

    public record NotificationView(
            Long id,
            NotificationType type,
            Short lagLevel,
            Long readingRecordId,
            Long clubId,
            String title,
            String body,
            Map<String, Object> payload,
            Instant scheduledAt,
            Instant sentAt,
            Instant openedAt
    ) {}

    public record NotificationSettingsRequest(
            NotifyTone notifyTone,
            @Min(0) @Max(23) Short quietHoursStart,
            @Min(0) @Max(23) Short quietHoursEnd,
            @Min(0) @Max(10) Short dailyNotifyCap,
            @Min(0) @Max(10) Short clubNotifyCap,
            Boolean allowNudge
    ) {}
}
