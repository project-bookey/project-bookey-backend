package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerImageView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;

/**
 * 배너·공지 이미지 업로드 — 프로필 사진과 같은 규칙(재인코딩 없이 매직넘버로 형식만 판별).
 * 올린 URL 을 배너 저장 요청의 imageUrl 에 넣는다. 저장소가 꺼져 있으면(운영 기본값) 503 — 그때는 URL 을 직접 넣는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerImageService {

    private static final int UPLOAD_RATE_LIMIT = 20;
    private static final int SNIFF_BYTES = 64 * 1024;

    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;
    private final Clock clock;

    public BannerImageView upload(AuthAdmin admin, MultipartFile file) {
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        rateLimiter.require("admin:banner-image:" + admin.id(), UPLOAD_RATE_LIMIT, Duration.ofMinutes(1));
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "올릴 파일이 비어 있어요.");
        }
        long size = file.getSize();
        if (size > properties.storage().image().maxBytes()) {
            throw ApiException.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageSniffer.ImageType type = ImageSniffer.sniff(readHead(file));
        if (type == null) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        String key = StorageKeys.forBannerImage(clock.instant(), type.extension());
        try (InputStream in = file.getInputStream()) {
            String url = storage.store(key, in, size, type.contentType());
            return new BannerImageView(url, type.width(), type.height());
        } catch (IOException e) {
            log.warn("배너 이미지를 읽지 못했습니다: adminId={}", admin.id(), e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }

    /** 배너가 더 쓰지 않는 이미지를 지운다(우리가 올린 파일만). 실패해도 배너 저장은 그대로 둔다. */
    public void deleteQuietly(String imageUrl) {
        String key = StorageKeys.bannerKeyOf(imageUrl);
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (Exception e) {
            log.warn("배너 이미지 삭제 실패(손으로 회수): key={}", key, e);
        }
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }
}
