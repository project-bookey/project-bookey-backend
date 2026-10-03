package app.bookey.api.inquiry;

import app.bookey.api.inquiry.dto.InquiryDtos.InquiryImageView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.inquiry.InquiryImage;
import app.bookey.domain.inquiry.InquiryImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

/**
 * 고객문의 사진 업로드 — 문의에 붙이기 전 임시 저장({@code PostImageService} 와 같은 흐름).
 * 업로드 시점에는 inquiry_id 가 비어 있고, 문의를 만들 때 imageIds 로 붙인다({@link InquiryService}).
 * 24시간 안에 안 붙으면 {@code InquiryImageCleanupJob} 이 파일과 행을 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryImageService {

    /** 도배 방지 — 1분에 10장(문의 하나에 3장까지라 넉넉하다). */
    static final int UPLOAD_RATE_LIMIT = 10;
    /** 형식 판별에 읽는 앞부분. */
    private static final int SNIFF_BYTES = 64 * 1024;

    private final InquiryImageRepository imageRepository;
    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;

    /** 일부러 @Transactional 을 두지 않는다 — 이유는 {@code PostImageService.upload} 와 같다. */
    public InquiryImageView upload(Long userId, MultipartFile file) {
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        rateLimiter.require("inquiry:image:" + userId, UPLOAD_RATE_LIMIT, Duration.ofMinutes(1));
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "업로드할 파일이 비어 있습니다.");
        }
        long size = file.getSize();
        if (size > properties.storage().image().maxBytes()) {
            throw ApiException.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageSniffer.ImageType type = ImageSniffer.sniff(readHead(file));
        if (type == null) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        String key = StorageKeys.forInquiryImage(userId, Instant.now(), type.extension());
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storage.store(key, in, size, type.contentType());
        } catch (IOException e) {
            log.warn("업로드 파일을 읽지 못했습니다: userId={}", userId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }

        InquiryImage image;
        try {
            image = imageRepository.save(InquiryImage.builder()
                    .userId(userId)
                    .storageKey(key)
                    .url(url)
                    .contentType(type.contentType())
                    .byteSize((int) size)
                    .width(type.width())
                    .height(type.height())
                    .build());
        } catch (RuntimeException e) {
            deleteOrphan(key, e);
            throw e;
        }
        return InquiryImageView.from(image);
    }

    /** 행 저장 실패의 보상 — 올린 파일을 지운다. 삭제까지 실패하면 키를 남기고 원래 예외에 suppressed 로 붙인다. */
    private void deleteOrphan(String key, RuntimeException cause) {
        log.warn("문의 사진 행 저장 실패 — 올린 파일을 지웁니다: key={}", key);
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("보상 삭제도 실패 — 수동 정리가 필요합니다: key={}", key, e);
            cause.addSuppressed(e);
        }
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            log.warn("업로드 파일의 앞부분을 읽지 못했습니다", e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }
}
