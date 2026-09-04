package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.PostImageView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.post.PostImage;
import app.bookey.domain.post.PostImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

/**
 * 독후감 사진 업로드 (§F7) — 글에 붙이기 전 임시 저장.
 * 업로드 시점에는 post_id 가 비어 있고, 독후감 작성·수정 때 imageIds 로 붙인다({@link PostService}).
 * 24시간 안에 안 붙으면 {@code PostImageCleanupJob} 이 파일과 행을 지운다.
 * 서버는 재인코딩·리사이즈를 하지 않는다 — 앱이 줄여 보내고, 여기서는 매직넘버로 형식만 판별한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostImageService {

    /** 도배 방지 — 1분에 30장. */
    private static final int UPLOAD_RATE_LIMIT = 30;
    /** 형식 판별에 읽는 앞부분. JPEG 의 SOF 가 이 안에 없으면 크기만 비운 채 저장한다. */
    private static final int SNIFF_BYTES = 64 * 1024;

    private final PostImageRepository imageRepository;
    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;

    /**
     * 일부러 @Transactional 을 두지 않는다 — DB 작업은 마지막 save 하나뿐이라 리포지토리 자체 트랜잭션으로 충분하고,
     * 파일 업로드(GCS 면 네트워크로 수 초)를 DB 트랜잭션 안에 두면 그동안 커넥션(풀 15)을 붙들게 된다.
     * 대신 파일을 올린 뒤 행 저장이 실패하면 파일을 바로 지운다 — 행 없는 파일은 정리 배치도 못 찾는 고아가 되므로.
     */
    public PostImageView upload(Long userId, MultipartFile file) {
        // 저장소가 꺼져 있으면(GCS 준비 전) 가장 먼저 거절한다 — 10MB 를 다 받아 놓고 버리지 않도록.
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        rateLimiter.require("post:image:" + userId, UPLOAD_RATE_LIMIT, Duration.ofMinutes(1));
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

        // 클라이언트 파일명은 쓰지 않는다 — 키는 사용자·시각·UUID 로만 만든다.
        String key = StorageKeys.forPostImage(userId, Instant.now(), type.extension());
        String url;
        // getInputStream() 은 부를 때마다 새 스트림이라 스니핑에 쓴 것과 별개로 다시 연다.
        try (InputStream in = file.getInputStream()) {
            url = storage.store(key, in, size, type.contentType());
        } catch (IOException e) {
            log.warn("업로드 파일을 읽지 못했습니다: userId={}", userId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }

        PostImage image;
        try {
            image = imageRepository.save(PostImage.builder()
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
        return new PostImageView(image.getId(), image.getUrl(), image.getWidth(), image.getHeight());
    }

    /** 행 저장 실패의 보상 — 올린 파일을 지운다. 삭제까지 실패하면 키를 남기고 원래 예외에 suppressed 로 붙인다. */
    private void deleteOrphan(String key, RuntimeException cause) {
        log.warn("사진 행 저장 실패 — 올린 파일을 지웁니다: key={}", key);
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("보상 삭제도 실패 — 수동 정리가 필요합니다: key={}", key, e);
            cause.addSuppressed(e);
        }
    }

    /** 파일 앞 최대 64KB — 매직넘버·크기 헤더는 이 안에 있다. */
    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            log.warn("업로드 파일의 앞부분을 읽지 못했습니다", e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }
}
