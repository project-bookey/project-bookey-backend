package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageConfigValidatorTest {

    private static BookeyProperties.Storage storage(String type, String bucket) {
        return new BookeyProperties.Storage(
                type,
                new BookeyProperties.Storage.Local("./uploads", ""),
                new BookeyProperties.Storage.Gcs(bucket),
                new BookeyProperties.Storage.Image(10485760L, 10)
        );
    }

    @Test
    @DisplayName("type=gcs 이고 버킷이 있으면 통과한다")
    void passesWithGcsAndBucket() {
        assertThatCode(() -> StorageConfigValidator.validate(storage("gcs", "bookey-media")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("대소문자·공백이 섞인 gcs 도 통과한다")
    void acceptsTypeIgnoringCaseAndSpaces() {
        assertThatCode(() -> StorageConfigValidator.validate(storage(" GCS ", "bookey-media")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영에서 로컬 디스크 저장소면 기동을 막는다 — 인스턴스 교체 때 사진이 사라진다")
    void rejectsLocalDiskInProd() {
        assertThatThrownBy(() -> StorageConfigValidator.validate(storage("local", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_TYPE=gcs");
    }

    @Test
    @DisplayName("type 이 비어 있어도 막는다 — 배포 env 가 통째로 덮어써 값이 빠진 경우")
    void rejectsBlankType() {
        assertThatThrownBy(() -> StorageConfigValidator.validate(storage("  ", "bookey-media")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bookey.storage.type");
    }

    @Test
    @DisplayName("type=gcs 인데 버킷이 비면 막는다 — 업로드가 전부 실패할 설정이다")
    void rejectsBlankBucket() {
        assertThatThrownBy(() -> StorageConfigValidator.validate(storage("gcs", " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GCS_BUCKET");
    }

    @Test
    @DisplayName("storage 설정 자체가 없으면 막는다")
    void rejectsMissingStorageSection() {
        assertThatThrownBy(() -> StorageConfigValidator.validate(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bookey.storage.type");
    }
}
