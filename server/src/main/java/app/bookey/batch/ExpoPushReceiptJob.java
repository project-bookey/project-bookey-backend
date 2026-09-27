package app.bookey.batch;

import app.bookey.common.config.BookeyProperties;
import app.bookey.domain.notification.ExpoPushTicket;
import app.bookey.domain.notification.ExpoPushTicketRepository;
import app.bookey.domain.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpoPushReceiptJob {

    private static final int BATCH_SIZE = 300;
    private static final short MAX_ATTEMPTS = 5;

    private final ExpoPushTicketRepository ticketRepository;
    private final UserDeviceRepository deviceRepository;
    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    @Scheduled(fixedDelay = 2 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    @Transactional
    public void checkReceipts() {
        Instant now = Instant.now();
        List<ExpoPushTicket> tickets = ticketRepository.findDue(
                now, org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE));
        if (tickets.isEmpty()) return;
        try {
            RestClient.RequestBodySpec request = bookApiRestClient.post()
                    .uri(properties.notification().expoReceiptUrl())
                    .contentType(MediaType.APPLICATION_JSON);
            if (!properties.notification().expoAccessToken().isBlank()) {
                request.header("Authorization", "Bearer " + properties.notification().expoAccessToken());
            }
            Map<?, ?> response = request.body(Map.of("ids", tickets.stream()
                            .map(ExpoPushTicket::getTicketId).toList()))
                    .retrieve().body(Map.class);
            Map<?, ?> receipts = response != null && response.get("data") instanceof Map<?, ?> data ? data : Map.of();
            for (ExpoPushTicket ticket : tickets) {
                Object raw = receipts.get(ticket.getTicketId());
                if (!(raw instanceof Map<?, ?> receipt)) {
                    retryOrFail(ticket, "RECEIPT_NOT_READY", now);
                    continue;
                }
                if ("ok".equals(receipt.get("status"))) {
                    ticket.delivered(now);
                    continue;
                }
                String code = errorCode(receipt);
                if ("DeviceNotRegistered".equals(code)) {
                    deviceRepository.findById(ticket.getDeviceId()).ifPresent(device -> device.disablePush());
                }
                ticket.failed(code, now);
                log.warn("Expo push delivery failed: ticket={} device={} error={}",
                        ticket.getTicketId(), ticket.getDeviceId(), code);
            }
        } catch (RestClientException e) {
            tickets.forEach(ticket -> retryOrFail(ticket, "RECEIPT_REQUEST_FAILED", now));
            log.warn("Expo receipt request failed for {} tickets", tickets.size(), e);
        }
    }

    private void retryOrFail(ExpoPushTicket ticket, String code, Instant now) {
        if (ticket.getAttemptCount() + 1 >= MAX_ATTEMPTS) ticket.failed(code, now);
        else ticket.retry(now);
    }

    private static String errorCode(Map<?, ?> receipt) {
        if (receipt.get("details") instanceof Map<?, ?> details && details.get("error") != null) {
            return String.valueOf(details.get("error"));
        }
        return receipt.get("message") == null ? "UNKNOWN" : String.valueOf(receipt.get("message"));
    }
}
