package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 운영(prod) 프로파일에서 업로드 저장소가 GCS 로 잡혔는지 기동 시점에 확인한다.
 *
 * <p>Cloud Run 컨테이너의 파일시스템은 쓰기가 되지만 인스턴스가 교체·확장될 때마다 사라진다.
 * 그래서 로컬 디스크 저장소로 뜨면 업로드는 200 으로 성공해 놓고 며칠 뒤 사진이 없어지고,
 * 인스턴스가 둘 이상이면 다른 인스턴스에서 {@code /uploads/{key}} 가 404 가 된다 — 조용히 데이터를 잃는 설정이다.
 * 배포 워크플로가 {@code env_vars_update_strategy: overwrite} 라 콘솔에서 손으로 넣은 환경변수도 다음 배포에서 지워지므로,
 * 잘못된 설정으로 뜨느니 기동을 막는다. Cloud Run 은 새 리비전이 못 뜨면 이전 리비전을 계속 서빙한다.
 */
@Slf4j
@Component
@Profile("prod")
public class StorageConfigValidator {

    public StorageConfigValidator(BookeyProperties properties) {
        validate(properties.storage());
        log.info("운영 업로드 저장소 확인: gs://{}", properties.storage().gcs().bucket());
    }

    /** 운영에서 허용하는 조합은 type=gcs + 비어 있지 않은 버킷, 이 하나뿐이다. */
    static void validate(BookeyProperties.Storage storage) {
        String type = storage == null ? null : storage.type();
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!"gcs".equals(normalized)) {
            throw new IllegalStateException(
                    "운영 프로파일에서는 bookey.storage.type 이 gcs 여야 합니다(현재: "
                            + (normalized.isEmpty() ? "(비어 있음)" : normalized)
                            + "). 로컬 디스크로 뜨면 인스턴스가 교체될 때마다 업로드한 사진이 사라집니다. "
                            + "배포 환경변수에 STORAGE_TYPE=gcs 를 넣으세요.");
        }
        String bucket = storage.gcs() == null ? null : storage.gcs().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(
                    "운영 프로파일에서는 bookey.storage.gcs.bucket 이 필요합니다. "
                            + "배포 환경변수에 GCS_BUCKET=<버킷 이름> 을 넣으세요.");
        }
    }
}
