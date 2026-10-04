package app.bookey.api.me;

import app.bookey.api.auth.AuthService;
import app.bookey.api.auth.dto.AuthDtos.MeResponse;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.AfterCommit;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 프로필 사진 업로드.
 * PostImageService 와 같은 저장 규칙 — 재인코딩 없이 매직넘버로 형식만 판별한다.
 * 사진을 바꾸면 이전 파일은 커밋 뒤에 지운다(우리 저장소에 올린 사진일 때만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    /** 도배 방지 — 1분에 5장. */
    private static final int UPLOAD_RATE_LIMIT = 5;
    private static final int SNIFF_BYTES = 64 * 1024;

    private final UserRepository userRepository;
    private final AuthService authService;
    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;

    @Transactional
    public MeResponse upload(Long userId, MultipartFile file) {
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        rateLimiter.require("me:avatar:" + userId, UPLOAD_RATE_LIMIT, Duration.ofMinutes(1));
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        String key = StorageKeys.forAvatar(userId, type.extension());
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storage.store(key, in, size, type.contentType());
        } catch (IOException e) {
            log.warn("아바타 파일을 읽지 못했습니다: userId={}", userId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
        String previousKey = StorageKeys.avatarKeyOf(userId, user.getAvatarUrl());
        user.updateProfile(null, url);
        if (previousKey != null && !previousKey.equals(key)) {
            AfterCommit.run(() -> deleteQuietly(previousKey));
        }
        return authService.toMe(user);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (Exception e) {
            log.warn("이전 프로필 사진 삭제 실패(손으로 회수): key={}", key, e);
        }
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            log.warn("아바타 파일의 앞부분을 읽지 못했습니다", e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }
}
