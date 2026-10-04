package app.bookey.api.social;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Google Play RTDN(Pub/Sub push). 알림 자체를 신뢰하지 않고 purchaseToken으로 Play API를 다시 조회한다. */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class GooglePlayWebhookController {
    private final SubscriptionService subscriptionService;
    private final BookeyProperties properties;
    private final ObjectMapper objectMapper;

    @PostMapping("/google")
    public ResponseEntity<Void> google(@RequestParam String token, @RequestBody PubSubEnvelope envelope) {
        String expected = properties.payment().google().rtdnToken();
        if (expected == null || expected.isBlank()) throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        if (!constantTimeEquals(expected, token)) throw ApiException.of(ErrorCode.FORBIDDEN);
        try {
            byte[] decoded = Base64.getDecoder().decode(envelope.message().data());
            GoogleRtdn notification = objectMapper.readValue(decoded, GoogleRtdn.class);
            if (!properties.payment().google().packageName().equals(notification.packageName())) {
                throw ApiException.of(ErrorCode.INVALID_REQUEST);
            }
            if (notification.subscriptionNotification() != null) {
                subscriptionService.syncGoogleSubscription(
                        notification.subscriptionNotification().purchaseToken());
            }
            return ResponseEntity.noContent().build();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Google Play 알림 형식이 올바르지 않습니다.");
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    public record PubSubEnvelope(PubSubMessage message) {}
    public record PubSubMessage(String data, String messageId) {}
    public record GoogleRtdn(String version, String packageName, long eventTimeMillis,
                             SubscriptionNotification subscriptionNotification) {}
    public record SubscriptionNotification(String version, int notificationType, String purchaseToken) {}
}
