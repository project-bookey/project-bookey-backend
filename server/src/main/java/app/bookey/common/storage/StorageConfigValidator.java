package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영(prod) 프로파일에서 업로드 저장소 설정이 성립하는지 기동 시점에 확인한다.
 *
 * <p>운영에서 허용하는 조합은 두 가지뿐이다 — <b>gcs + 비어 있지 않은 버킷</b>(업로드 켬) 또는
 * <b>none</b>(업로드 끔, {@link NoopStorageService}). 로컬 디스크는 막는다:
 * Cloud Run 컨테이너의 파일시스템은 쓰기가 되지만 인스턴스가 교체·확장될 때마다 사라져서,
 * 업로드는 200 으로 성공해 놓고 며칠 뒤 사진이 없어지고 인스턴스가 둘 이상이면 다른 인스턴스에서
 * {@code /uploads/{key}} 가 404 가 된다 — 조용히 데이터를 잃는 설정이라 차라리 기동을 막는다.
 * 배포 워크플로가 {@code env_vars_update_strategy: overwrite} 라 콘솔에서 손으로 넣은 환경변수도
 * 다음 배포에서 지워지므로, 잘못된 설정으로 뜨느니 막는 편이 낫다. Cloud Run 은 새 리비전이 못 뜨면
 * 이전 리비전을 계속 서빙한다.
 *
 * <p>{@code none} 은 버킷이 준비되기 전에도 배포가 되게 하는 임시 상태다 — 사진 업로드만 503 으로 거절하고
 * 나머지 기능은 정상 동작한다. 기동을 막는 대신 {@link #DISABLED_WARNING} 을 warn 으로 남겨,
 * 로그만 보고도 지금 업로드가 꺼져 있다는 것과 켜는 방법을 알 수 있게 한다.
 * 버킷 생성·권한·워크플로 두 줄 추가 순서는 README "GCS 준비 (운영)" 에 있다.
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

    /** 업로드를 꺼 둔 채 뜰 때 남기는 경고 — 켜는 방법을 그대로 담는다. */
    static final String DISABLED_WARNING = "운영 사진 업로드가 꺼져 있습니다 — GCS 준비 후 "
            + WORKFLOW_ENV_VARS + " 에 STORAGE_TYPE=gcs, GCS_BUCKET 을 넣으세요.";

    /** 운영에서 허용하는 두 상태. */
    enum Mode {
        /** GCS 버킷에 저장한다 — 업로드가 켜져 있다. */
        GCS,
        /** 업로드를 받지 않는다({@code type=none}) — 나머지 기능은 정상. */
        DISABLED
    }

    public StorageConfigValidator(BookeyProperties properties) {
        if (validate(properties.storage()) == Mode.DISABLED) {
            log.warn(DISABLED_WARNING);
        } else {
            log.info("운영 업로드 저장소 확인: gs://{}", properties.storage().gcs().bucket());
        }
    }

    /**
     * 운영에서 허용하는 조합은 type=gcs + 비어 있지 않은 버킷, 또는 type=none 두 가지다.
     * 판정 규칙은 실제로 빈을 고르는 {@code @ConditionalOnProperty} 와 똑같이 맞춘다 —
     * 대소문자만 무시하고 공백은 다듬지 않는다. 여기서만 " gcs " 를 통과시키면
     * 검사는 통과했는데 로컬 디스크 구현이 뜨는, 정확히 이 검사가 막으려던 상태가 된다.
     */
    static Mode validate(BookeyProperties.Storage storage) {
        String type = storage == null ? null : storage.type();
        if ("none".equalsIgnoreCase(type)) {
            return Mode.DISABLED;
        }
        if (!"gcs".equalsIgnoreCase(type)) {
            throw new IllegalStateException(
                    "운영 프로파일에서는 bookey.storage.type 이 gcs(업로드 켬) 또는 none(업로드 끔) 이어야 합니다(현재: "
                            + (type == null || type.isBlank() ? "(비어 있음)" : type)
                            + "). 로컬 디스크로 뜨면 인스턴스가 교체될 때마다 업로드한 사진이 사라집니다. "
                            + WORKFLOW_ENV_VARS + " 에 STORAGE_TYPE=gcs 와 GCS_BUCKET 을 넣거나, "
                            + "그 줄을 지워 prod 기본값 none 으로 두세요.");
        }
        requireBucket(storage);
        return Mode.GCS;
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
