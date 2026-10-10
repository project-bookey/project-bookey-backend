package app.bookey.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SendTimeResolverTest {

    private static Instant kst(String local) {
        return ZonedDateTime.parse(local + "+09:00[Asia/Seoul]").toInstant();
    }

    @Test
    @DisplayName("자정을 넘는 방해 금지(22~8시) — 23시면 다음 날 8시로 미룬다")
    void quietHoursAcrossMidnight() {
        assertThat(SendTimeResolver.isQuietHour(23, 22, 8)).isTrue();
        assertThat(SendTimeResolver.isQuietHour(7, 22, 8)).isTrue();
        assertThat(SendTimeResolver.isQuietHour(8, 22, 8)).isFalse();
        assertThat(SendTimeResolver.campaignSendTime(kst("2026-10-10T23:30:00"), SendTimeResolver.KST, 22, 8, false))
                .isEqualTo(kst("2026-10-11T08:00:00"));
    }

    @Test
    @DisplayName("광고는 21시 이후면 다음 날 8시로, 방해 금지를 쓰지 않는 회원도 마찬가지")
    void marketingNightWindow() {
        assertThat(SendTimeResolver.campaignSendTime(kst("2026-10-10T21:10:00"), SendTimeResolver.KST, 0, 0, true))
                .isEqualTo(kst("2026-10-11T08:00:00"));
        assertThat(SendTimeResolver.campaignSendTime(kst("2026-10-10T06:00:00"), SendTimeResolver.KST, 0, 0, true))
                .isEqualTo(kst("2026-10-10T08:00:00"));
        assertThat(SendTimeResolver.campaignSendTime(kst("2026-10-10T14:00:00"), SendTimeResolver.KST, 22, 8, true))
                .isEqualTo(kst("2026-10-10T14:00:00"));
    }

    @Test
    @DisplayName("두 규칙이 서로 밀어내면 둘 다 만족하는 시각까지 미룬다 — 방해 금지 2~9시, 광고는 8시부터")
    void bothRules() {
        assertThat(SendTimeResolver.campaignSendTime(kst("2026-10-10T22:00:00"), SendTimeResolver.KST, 2, 9, true))
                .isEqualTo(kst("2026-10-11T09:00:00"));
    }
}
