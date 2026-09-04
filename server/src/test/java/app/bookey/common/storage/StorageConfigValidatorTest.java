package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("type=gcs 이고 버킷이 있으면 통과하고 저장소를 켠 상태로 본다")
    void passesWithGcsAndBucket() {
        assertThat(StorageConfigValidator.validate(storage("gcs", "bookey-media")))
                .isEqualTo(StorageConfigValidator.Mode.GCS);
    }

    @Test
    @DisplayName("대소문자만 다른 gcs 도 통과한다 — @ConditionalOnProperty 와 같은 규칙")
    void acceptsTypeIgnoringCase() {
        assertThat(StorageConfigValidator.validate(storage("GCS", "bookey-media")))
                .isEqualTo(StorageConfigValidator.Mode.GCS);
        assertThat(StorageConfigValidator.validate(storage("NONE", "")))
                .isEqualTo(StorageConfigValidator.Mode.DISABLED);
    }

    @Test
    @DisplayName("type=none 은 통과하되 업로드가 꺼진 상태로 본다 — 버킷이 없어도 배포는 되어야 한다")
    void allowsDisabledStorage() {
        assertThat(StorageConfigValidator.validate(storage("none", "")))
                .isEqualTo(StorageConfigValidator.Mode.DISABLED);
    }

    @Test
    @DisplayName("업로드가 꺼졌다는 경고에 켜는 방법(고칠 파일과 넣을 두 줄)이 그대로 적혀 있다")
    void disabledWarningTellsHowToTurnItOn() {
        assertThat(StorageConfigValidator.DISABLED_WARNING)
                .contains(".github/workflows/deploy-cloud-run.yml")
                .contains("STORAGE_TYPE=gcs")
                .contains("GCS_BUCKET");
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
    @DisplayName("기동 실패 메시지가 고칠 파일(배포 워크플로 env_vars)과 넣을 줄을 그대로 알려 준다")
    void failureMessagesPointAtTheWorkflowLine() {
        assertThatThrownBy(() -> StorageConfigValidator.validate(storage("gcs", "")))
                .hasMessageContaining(".github/workflows/deploy-cloud-run.yml")
                .hasMessageContaining("GCS_BUCKET=${{ vars.GCP_MEDIA_BUCKET }}")
                .hasMessageContaining("GCP_MEDIA_BUCKET");
        assertThatThrownBy(() -> StorageConfigValidator.validate(storage("local", "bookey-media")))
                .hasMessageContaining(".github/workflows/deploy-cloud-run.yml")
                .hasMessageContaining("STORAGE_TYPE=gcs")
                .hasMessageContaining("none");
    }

    @Test
    @DisplayName("requireBucket — GcsStorageService 생성자가 같은 검사·같은 안내를 쓴다 (validator 보다 먼저 만들어져도 로그가 같다)")
    void requireBucketSharesTheSameGuidance() {
        assertThat(StorageConfigValidator.requireBucket(storage("gcs", "bookey-media"))).isEqualTo("bookey-media");
        assertThatThrownBy(() -> StorageConfigValidator.requireBucket(storage("gcs", "  ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bookey.storage.gcs.bucket")
                .hasMessageContaining(".github/workflows/deploy-cloud-run.yml")
                .hasMessageContaining("GCS_BUCKET=${{ vars.GCP_MEDIA_BUCKET }}");
        assertThatThrownBy(() -> StorageConfigValidator.requireBucket(null))
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
