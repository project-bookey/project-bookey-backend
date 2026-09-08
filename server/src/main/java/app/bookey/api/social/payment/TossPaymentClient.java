package app.bookey.api.social.payment;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Toss Payments 서버 API — 결제창 생성과 승인(confirm)은 반드시 서버 시크릿 키로 수행한다. */
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private static final String API = "https://api.tosspayments.com/v1/payments";

    private final RestClient bookApiRestClient;

    @SuppressWarnings("unchecked")
    public String createCheckoutUrl(String secretKey, TossCreatePaymentRequest request) {
        try {
            Map<String, Object> body = bookApiRestClient.post()
                    .uri(API)
                    .header(HttpHeaders.AUTHORIZATION, basic(secretKey))
                    .body(Map.of(
                            "method", "CARD",
                            "amount", request.amount(),
                            "orderId", request.orderId(),
                            "orderName", request.orderName(),
                            "successUrl", request.successUrl(),
                            "failUrl", request.failUrl(),
                            "customerKey", request.customerKey(),
                            "appScheme", request.appScheme()))
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> checkout = body == null ? null : (Map<String, Object>) body.get("checkout");
            Object url = checkout == null ? null : checkout.get("url");
            if (!(url instanceof String checkoutUrl) || checkoutUrl.isBlank()) {
                throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
            }
            return checkoutUrl;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Toss 결제창을 만들지 못했습니다.");
        }
    }

    public TossPayment confirm(String secretKey, String paymentKey, String orderId, int amount) {
        try {
            Map<?, ?> body = bookApiRestClient.post()
                    .uri(API + "/confirm")
                    .header(HttpHeaders.AUTHORIZATION, basic(secretKey))
                    .header("Idempotency-Key", paymentKey)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
            }
            return new TossPayment(
                    string(body.get("paymentKey")),
                    string(body.get("orderId")),
                    intValue(body.get("totalAmount")),
                    string(body.get("status")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Toss 결제를 승인하지 못했습니다.");
        }
    }

    private static String basic(String secretKey) {
        String token = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    public record TossCreatePaymentRequest(String orderId, String orderName, int amount, String customerKey,
                                           String successUrl, String failUrl, String appScheme) {}

    public record TossPayment(String paymentKey, String orderId, int totalAmount, String status) {}
}
