package app.bookey.domain.admin;

import app.bookey.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SanctionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");

    private static UserSanction sanction(SanctionType type, Instant endsAt) {
        return new UserSanction(1L, 9L, type, "사유", endsAt);
    }

    @Test
    @DisplayName("살아 있는 제재가 없으면 ACTIVE — 기간 없는 경고는 상태를 바꾸지 않는다")
    void warnDoesNotChangeStatus() {
        assertThat(SanctionPolicy.statusFor(List.of(), NOW)).isEqualTo(UserStatus.ACTIVE);
        assertThat(SanctionPolicy.statusFor(List.of(sanction(SanctionType.WARN, null)), NOW))
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("여러 제재가 겹치면 가장 무거운 것이 상태가 된다")
    void heaviestWins() {
        var sanctions = List.of(
                sanction(SanctionType.WRITE_BAN, null),
                sanction(SanctionType.SUSPEND, NOW.plusSeconds(3600)),
                sanction(SanctionType.WARN, null));
        assertThat(SanctionPolicy.statusFor(sanctions, NOW)).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("기간이 끝났거나 해제된 제재는 세지 않는다")
    void expiredAndReleasedIgnored() {
        UserSanction released = sanction(SanctionType.TERMINATE, null);
        released.release();
        var sanctions = List.of(
                sanction(SanctionType.SUSPEND, NOW.minusSeconds(1)),
                released,
                sanction(SanctionType.WRITE_BAN, NOW.plusSeconds(60)));
        assertThat(SanctionPolicy.statusFor(sanctions, NOW)).isEqualTo(UserStatus.WRITE_BANNED);
    }

    @Test
    @DisplayName("heavier — 새 제재가 이미 걸린 더 무거운 상태를 낮추지 않는다")
    void heavierNeverDowngrades() {
        assertThat(SanctionPolicy.heavier(UserStatus.SUSPENDED, UserStatus.WRITE_BANNED))
                .isEqualTo(UserStatus.SUSPENDED);
        assertThat(SanctionPolicy.heavier(UserStatus.ACTIVE, UserStatus.TERMINATED))
                .isEqualTo(UserStatus.TERMINATED);
    }
}
