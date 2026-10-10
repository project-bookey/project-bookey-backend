package app.bookey.domain.appconfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AppReleaseConfigTest {

    private static AppReleaseConfig config(String min, String latest) throws Exception {
        Constructor<AppReleaseConfig> constructor = AppReleaseConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AppReleaseConfig config = constructor.newInstance();
        config.update(min, latest, null, null, 1L, Instant.EPOCH);
        return config;
    }

    @Test
    @DisplayName("최소 지원 버전보다 낮으면 강제, 최신보다만 낮으면 권장")
    void requiredAndRecommended() throws Exception {
        AppReleaseConfig config = config("1.2.0", "1.4.0");
        assertThat(config.requiresUpdate("1.1.9")).isTrue();
        assertThat(config.requiresUpdate("1.2.0")).isFalse();
        assertThat(config.recommendsUpdate("1.3.5")).isTrue();
        assertThat(config.recommendsUpdate("1.4.0")).isFalse();
    }
}
