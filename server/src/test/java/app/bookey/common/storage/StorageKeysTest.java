package app.bookey.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageKeysTest {

    @Test
    @DisplayName("키는 posts/{userId}/yyyy/MM/{uuid}.{ext} 형식이다")
    void buildsPostImageKey() {
        String key = StorageKeys.forPostImage(7L, Instant.parse("2026-09-02T01:00:00Z"), "jpg");

        assertThat(key).matches("posts/7/2026/09/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");
    }

    @Test
    @DisplayName("연·월은 UTC 로 끊는다 — KST 로 해가 바뀐 시각이어도 UTC 기준으로 적는다")
    void usesUtcForYearAndMonth() {
        // 2026-01-01T00:30+09:00 == 2025-12-31T15:30Z
        String key = StorageKeys.forPostImage(1L, Instant.parse("2025-12-31T15:30:00Z"), "png");

        assertThat(key).startsWith("posts/1/2025/12/");
    }

    @Test
    @DisplayName("같은 시각·같은 사용자라도 매번 다른 키를 만든다")
    void generatesUniqueKeys() {
        Instant now = Instant.parse("2026-09-02T01:00:00Z");

        assertThat(StorageKeys.forPostImage(1L, now, "webp"))
                .isNotEqualTo(StorageKeys.forPostImage(1L, now, "webp"));
    }

    @Test
    @DisplayName("경로 구분자나 점이 섞인 확장자는 거부한다")
    void rejectsUnsafeExtension() {
        Instant now = Instant.parse("2026-09-02T01:00:00Z");

        assertThatThrownBy(() -> StorageKeys.forPostImage(1L, now, "../evil"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.forPostImage(1L, now, "a/b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.forPostImage(1L, now, "tar.gz"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.forPostImage(1L, now, "a\\b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.forPostImage(1L, now, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.forPostImage(1L, now, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
