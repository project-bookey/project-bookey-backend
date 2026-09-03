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
 *
 * <p>따라서 배포 워크플로({@code .github/workflows/deploy-cloud-run.yml})의 {@code env_vars} 에 {@code GCS_BUCKET} 이 없으면
 * 새 리비전은 startup probe 에서 실패하고 deploy 스텝이 빨갛게 된다 — 장애는 아니지만 배포 불가 상태다.
 * 버킷 생성·권한·워크플로 한 줄 추가 순서는 README "GCS 준비 (운영)" 에 있다.
 *
 * <p>버킷이 빈 경우는 보통 이 빈보다 먼저 만들어지는 {@link GcsStorageService} 생성자가 먼저 잡는다
 * ({@code api.post.PostImageService} 가 저장소를 생성자 주입하므로 {@code @Lazy} 여도 기동 시점에 생성된다).
 * 어느 쪽이 먼저 막든 로그가 같은 안내를 보이도록 버킷 검사·메시지는 {@link #requireBucket} 하나로 공유한다.
 */
@Slf4j
@Component
@Profile("prod")
public class StorageConfigValidator {

    /** 기동 실패 로그만 보고도 고칠 수 있게, 메시지에 넣어야 할 파일과 줄을 그대로 적는다. */
    static final String WORKFLOW_ENV_VARS = ".github/workflows/deploy-cloud-run.yml 의 env_vars";

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
                            + WORKFLOW_ENV_VARS + " 에 STORAGE_TYPE=gcs 를 넣으세요.");
        }
        requireBucket(storage);
    }

    /**
     * 비어 있지 않은 버킷 이름을 돌려주고, 비어 있으면 고칠 파일과 줄을 적은 IllegalStateException 을 던진다.
     * {@link GcsStorageService} 생성자와 {@link #validate} 가 함께 쓴다.
     */
    static String requireBucket(BookeyProperties.Storage storage) {
        String bucket = storage == null || storage.gcs() == null ? null : storage.gcs().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(
                    "bookey.storage.gcs.bucket 이 비어 있습니다. 환경변수 GCS_BUCKET=<버킷 이름> 이 필요합니다 — 운영이면 "
                            + WORKFLOW_ENV_VARS + " 에 GCS_BUCKET=${{ vars.GCP_MEDIA_BUCKET }} 을 넣고 "
                            + "GitHub Variables 에 GCP_MEDIA_BUCKET 을 등록하세요(README 'GCS 준비 (운영)' 순서대로).");
        }
        return bucket;
    }
}
