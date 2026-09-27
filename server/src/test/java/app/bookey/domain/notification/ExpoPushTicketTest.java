package app.bookey.domain.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExpoPushTicketTest {

    @Test
    void retryUsesBackoffAndIncrementsAttempt() {
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        ExpoPushTicket ticket = new ExpoPushTicket("ticket-1", 1L, 2L, now);

        ticket.retry(now);

        assertThat(ticket.getAttemptCount()).isEqualTo((short) 1);
        assertThat(ticket.getNextCheckAt()).isEqualTo(now.plusSeconds(120));
        assertThat(ticket.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void terminalStatesRecordResult() {
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        ExpoPushTicket delivered = new ExpoPushTicket("ticket-1", 1L, 2L, now);
        ExpoPushTicket failed = new ExpoPushTicket("ticket-2", 1L, 2L, now);

        delivered.delivered(now);
        failed.failed("DeviceNotRegistered", now);

        assertThat(delivered.getStatus()).isEqualTo("DELIVERED");
        assertThat(delivered.getResolvedAt()).isEqualTo(now);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorCode()).isEqualTo("DeviceNotRegistered");
    }
}
