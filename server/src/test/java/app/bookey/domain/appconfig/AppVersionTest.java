package app.bookey.domain.appconfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionTest {

    @Test
    @DisplayName("자리마다 숫자로 비교하고, 빠진 자리는 0 으로 본다")
    void compare() {
        assertThat(AppVersion.compare("1.10.0", "1.9.9")).isPositive();
        assertThat(AppVersion.compare("1.2", "1.2.0")).isZero();
        assertThat(AppVersion.compare("0.9.12", "1.0.0")).isNegative();
    }

    @Test
    @DisplayName("앱이 보낸 버전을 알 수 없으면 강제 업데이트하지 않는다")
    void unknownVersionIsNotBelow() {
        assertThat(AppVersion.isBelow(null, "1.0.0")).isFalse();
        assertThat(AppVersion.isBelow("1.0.0-beta", "2.0.0")).isFalse();
        assertThat(AppVersion.isBelow("0.9.0", "1.0.0")).isTrue();
        assertThat(AppVersion.isBelow("1.0.0", "1.0.0")).isFalse();
    }

    @Test
    @DisplayName("버전 형식 — 숫자와 점, 세 자리까지")
    void valid() {
        assertThat(AppVersion.isValid("1.2.3")).isTrue();
        assertThat(AppVersion.isValid("2")).isTrue();
        assertThat(AppVersion.isValid("1.2.3.4")).isFalse();
        assertThat(AppVersion.isValid("v1.2")).isFalse();
    }
}
