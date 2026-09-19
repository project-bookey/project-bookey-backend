package app.bookey.common.storage;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * 운영용 S3 저장소. 버킷은 공개 읽기 정책을 두고 URL 을 그대로 내려준다.
 * 자격증명은 EC2 instance profile 같은 AWS 기본 credential chain 으로 잡는다.
 */
@Slf4j
@Service
@Lazy
@ConditionalOnProperty(prefix = "bookey.storage", name = "type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final String bucket;
    private final String publicBaseUrl;
    private final S3Client s3;

    public S3StorageService(BookeyProperties properties) {
        BookeyProperties.Storage.S3 config = StorageConfigValidator.requireS3(properties.storage());
        this.bucket = config.bucket();
        String region = config.region() == null || config.region().isBlank() ? "ap-northeast-2" : config.region();
        this.publicBaseUrl = resolvePublicBaseUrl(config.publicBaseUrl(), bucket, region);
        this.s3 = S3Client.builder()
                .region(Region.of(region))
                .build();
        log.info("S3 업로드 저장소: s3://{} ({})", bucket, region);
    }

    @Override
    public String store(String key, InputStream in, long size, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
        try {
            s3.putObject(request, RequestBody.fromInputStream(in, size));
        } catch (Exception e) {
            log.warn("S3 업로드 실패: key={}", key, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("S3 삭제 실패(무시): key={}", key, e);
        }
    }

    private static String resolvePublicBaseUrl(String configured, String bucket, String region) {
        if (configured != null && !configured.isBlank()) {
            return stripTrailingSlashes(configured);
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }

    private static String stripTrailingSlashes(String value) {
        String out = value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
