package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.ClubHomeView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.Club;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 클럽 머리 배경 사진 — 호스트만 올리고 뺀다. 재인코딩 없이 매직넘버로 형식만 판별한다(PostImageService 와 같은 규칙).
 *
 * <p>일부러 @Transactional 을 두지 않는다 — 파일 업로드 동안 커넥션을 붙들지 않도록. DB 쓰기는
 * {@link ClubService#changeBackground} 한 번이고, 그게 실패하면 방금 올린 파일을 지운다.
 * 바꾸거나 빼면 이전 파일도 지운다(실패해도 배경은 이미 바뀌었으니 경고만 남긴다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubBackgroundService {

    /** 도배 방지 — 1분에 5장. */
    private static final int UPLOAD_RATE_LIMIT = 5;
    private static final int SNIFF_BYTES = 64 * 1024;

    private final ClubService clubService;
    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;

    public ClubHomeView upload(Long userId, Long clubId, MultipartFile file) {
        requireHost(userId, clubId);
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        rateLimiter.require("club:background:" + userId, UPLOAD_RATE_LIMIT, Duration.ofMinutes(1));
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

        String key = StorageKeys.forClubBackground(clubId, type.extension());
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storage.store(key, in, size, type.contentType());
        } catch (IOException e) {
            log.warn("클럽 배경 사진을 읽지 못했습니다: clubId={}", clubId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }

        String previous;
        try {
            previous = clubService.changeBackground(userId, clubId, url, key);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        deleteQuietly(previous);
        return clubService.home(userId, clubId);
    }

    public ClubHomeView remove(Long userId, Long clubId) {
        requireHost(userId, clubId);
        deleteQuietly(clubService.changeBackground(userId, clubId, null, null));
        return clubService.home(userId, clubId);
    }

    /** 파일을 받기 전에 호스트인지 먼저 본다 — 남의 클럽에 10MB 를 올려 놓고 거절하지 않도록. */
    private void requireHost(Long userId, Long clubId) {
        Club club = clubService.getClub(clubId);
        if (!club.isHost(userId)) {
            throw ApiException.of(ErrorCode.CLUB_NOT_HOST);
        }
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("이전 클럽 배경 사진을 지우지 못했습니다 — 수동 정리가 필요합니다: key={}", key, e);
        }
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            log.warn("클럽 배경 사진의 앞부분을 읽지 못했습니다", e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }
}
