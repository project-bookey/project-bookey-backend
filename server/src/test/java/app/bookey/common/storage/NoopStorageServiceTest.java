package app.bookey.common.storage;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoopStorageServiceTest {

    private final NoopStorageService storage = new NoopStorageService();

    @Test
    @DisplayName("enabled() 가 false — 업로드 API 가 파일을 읽기 전에 거절할 수 있게 한다")
    void reportsDisabled() {
        assertThat(storage.enabled()).isFalse();
    }

    @Test
    @DisplayName("store 는 STORAGE_DISABLED — 파일을 조용히 버리지 않고 분명히 거절한다")
    void storeRejectsWithStorageDisabled() {
        InputStream in = new ByteArrayInputStream(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> storage.store("posts/1/2026/09/x.png", in, 3, "image/png"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORAGE_DISABLED));
    }

    @Test
    @DisplayName("delete 는 아무것도 하지 않는다 — 고아 사진 정리 배치가 예외로 죽으면 안 된다")
    void deleteDoesNothing() {
        assertThatCode(() -> storage.delete("posts/1/2026/09/x.png")).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete("")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("STORAGE_DISABLED 는 503 — 잘못 보낸 요청이 아니라 서버가 아직 못 받는 상태임을 알린다")
    void storageDisabledIsServiceUnavailable() {
        assertThat(ErrorCode.STORAGE_DISABLED.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
