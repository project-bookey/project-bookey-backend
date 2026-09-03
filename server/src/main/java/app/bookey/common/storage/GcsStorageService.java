package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 운영용 GCS 저장소. 버킷은 공개 읽기(allUsers: objectViewer)로 두고 URL 을 그대로 내려준다.
 * 자격증명은 ADC(Cloud Run 서비스계정)로 잡는다.
 *
 * <p>{@code @Lazy} 를 달아 두었지만 {@code PostImageService}·{@code PostImageCleanupJob} 이 저장소를 생성자 주입하므로
 * 실제로는 기동 시점에 만들어진다. 그래서 버킷이 비면 {@link StorageConfigValidator} 보다 이 생성자가 먼저 기동을 막는다 —
 * 검사와 안내 메시지는 {@link StorageConfigValidator#requireBucket} 을 같이 써서 어느 쪽이 먼저든 로그가 같다.
 */
@Slf4j
@Service
@Lazy
@ConditionalOnProperty(prefix = "bookey.storage", name = "type", havingValue = "gcs")
public class GcsStorageService implements StorageService {

    /** 사진은 키가 바뀌지 않으면 내용도 바뀌지 않는다 — 1년 immutable 캐시. */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final String bucket;
    private final Storage storage;

    public GcsStorageService(BookeyProperties properties) {
        this.bucket = StorageConfigValidator.requireBucket(properties.storage());
        this.storage = StorageOptions.getDefaultInstance().getService();
        log.info("GCS 업로드 저장소: gs://{}", bucket);
    }

    @Override
    public String store(String key, InputStream in, long size, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, key))
                .setContentType(contentType)
                .setCacheControl(CACHE_CONTROL)
                .build();
        try {
            storage.createFrom(blobInfo, in);
        } catch (Exception e) {
            log.warn("GCS 업로드 실패: key={}", key, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
        return "https://storage.googleapis.com/" + bucket + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            storage.delete(BlobId.of(bucket, key));   // 없는 키면 false 를 돌려줄 뿐 예외는 아니다
        } catch (Exception e) {
            log.warn("GCS 삭제 실패(무시): key={}", key, e);
        }
    }
}
