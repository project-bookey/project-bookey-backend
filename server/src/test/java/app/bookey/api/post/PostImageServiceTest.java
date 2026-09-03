package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.PostImageView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.post.PostImage;
import app.bookey.domain.post.PostImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 업로드 규칙 단위 테스트 — 리포지토리만 Mockito 로 대신하고(AuthServiceTest 선례),
 * StorageService·RateLimiter 는 손으로 쓴 페이크를 쓴다. Spring 컨텍스트 없음.
 */
class PostImageServiceTest {

    private static final long USER_ID = 10L;
    private static final long MAX_BYTES = 1024;

    /** 저장 호출 인자를 기록하고 고정 URL 을 돌려주는 페이크. */
    private static final class RecordingStorage implements StorageService {
        String key;
        String contentType;
        long size;
        byte[] content;
        int storeCalls;

        @Override
        public String store(String key, InputStream in, long size, String contentType) {
            storeCalls++;
            this.key = key;
            this.size = size;
            this.contentType = contentType;
            try {
                this.content = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return "http://localhost:8098/uploads/" + key;
        }

        @Override
        public void delete(String key) {
        }
    }

    /** Redis 없이 호출 키만 기록하고 항상 허용한다. */
    private static final class RecordingRateLimiter extends RateLimiter {
        final List<String> keys = new ArrayList<>();
        int limit;
        Duration window;

        RecordingRateLimiter() {
            super(null);
        }

        @Override
        public boolean tryAcquire(String key, int limit, Duration window) {
            keys.add(key);
            this.limit = limit;
            this.window = window;
            return true;
        }
    }

    private final PostImageRepository imageRepository = mock(PostImageRepository.class);
    private final RecordingStorage storage = new RecordingStorage();
    private final RecordingRateLimiter rateLimiter = new RecordingRateLimiter();
    private final PostImageService service =
            new PostImageService(imageRepository, storage, rateLimiter, properties(MAX_BYTES));

    private static BookeyProperties properties(long maxBytes) {
        return new BookeyProperties(null, null, null, null, new BookeyProperties.Storage("local",
                new BookeyProperties.Storage.Local("./uploads", ""),
                new BookeyProperties.Storage.Gcs(""),
                new BookeyProperties.Storage.Image(maxBytes, 10)));
    }

    /** PNG 시그니처 + IHDR(너비·높이) — 스니퍼가 크기를 읽는 앞 24바이트 뒤에 채움을 붙여 원하는 길이로 만든다. */
    private static byte[] png(int width, int height, int totalLength) {
        byte[] bytes = new byte[Math.max(totalLength, 24)];
        byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52};
        System.arraycopy(header, 0, bytes, 0, header.length);
        putInt(bytes, 16, width);
        putInt(bytes, 20, height);
        return bytes;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private void stubSaveAssigningId(long id) {
        when(imageRepository.save(any())).thenAnswer(invocation -> {
            PostImage image = invocation.getArgument(0);
            set(image, "id", id);
            return image;
        });
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("정상 PNG — 저장소에 posts/{userId}/yyyy/MM/{uuid}.png 키와 스니핑한 contentType 을 넘기고 내용을 그대로 쓴다")
    void storesPngUnderUserKeyWithSniffedContentType() {
        stubSaveAssigningId(42L);
        byte[] content = png(800, 600, 100);

        // 클라이언트가 보낸 파일명·Content-Type 은 믿지 않는다 — 거짓말을 해도 매직넘버로 png 로 잡힌다.
        service.upload(USER_ID, file("evil.txt", "text/plain", content));

        assertThat(storage.storeCalls).isEqualTo(1);
        assertThat(storage.key).matches("posts/10/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        assertThat(storage.contentType).isEqualTo("image/png");
        assertThat(storage.size).isEqualTo(100L);
        assertThat(storage.content).isEqualTo(content);
    }

    @Test
    @DisplayName("정상 PNG — post_id 없는 행을 저장하고 id·url·width·height 를 채운 PostImageView 를 돌려준다")
    void savesDetachedRowAndReturnsView() {
        stubSaveAssigningId(42L);

        PostImageView view = service.upload(USER_ID, file("x.png", "image/png", png(800, 600, 100)));

        ArgumentCaptor<PostImage> saved = ArgumentCaptor.forClass(PostImage.class);
        verify(imageRepository).save(saved.capture());
        PostImage row = saved.getValue();
        assertThat(row.getUserId()).isEqualTo(USER_ID);
        assertThat(row.isDetached()).isTrue();
        assertThat(row.getStorageKey()).isEqualTo(storage.key);
        assertThat(row.getUrl()).isEqualTo("http://localhost:8098/uploads/" + storage.key);
        assertThat(row.getContentType()).isEqualTo("image/png");
        assertThat(row.getByteSize()).isEqualTo(100);
        assertThat(row.getWidth()).isEqualTo(800);
        assertThat(row.getHeight()).isEqualTo(600);

        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.url()).isEqualTo(row.getUrl());
        assertThat(view.width()).isEqualTo(800);
        assertThat(view.height()).isEqualTo(600);
    }

    @Test
    @DisplayName("크기 제한을 넘으면 IMAGE_TOO_LARGE — 저장소·DB 를 건드리지 않는다")
    void rejectsOversizedFile() {
        byte[] tooBig = png(1, 1, (int) MAX_BYTES + 1);

        assertThatThrownBy(() -> service.upload(USER_ID, file("x.png", "image/png", tooBig)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE));
        assertThat(storage.storeCalls).isZero();
        verify(imageRepository, never()).save(any());
    }

    @Test
    @DisplayName("제한과 같은 크기는 허용한다")
    void allowsFileExactlyAtLimit() {
        stubSaveAssigningId(1L);

        service.upload(USER_ID, file("x.png", "image/png", png(1, 1, (int) MAX_BYTES)));

        assertThat(storage.storeCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("매직넘버가 JPEG·PNG·WebP 가 아니면 UNSUPPORTED_IMAGE_TYPE — Content-Type 이 image/png 라고 해도 믿지 않는다")
    void rejectsNonImageBytes() {
        byte[] text = "hello, not an image".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload(USER_ID, file("x.png", "image/png", text)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE));
        assertThat(storage.storeCalls).isZero();
        verify(imageRepository, never()).save(any());
    }

    @Test
    @DisplayName("빈 파일은 INVALID_REQUEST")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload(USER_ID, file("x.png", "image/png", new byte[0])))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(storage.storeCalls).isZero();
    }

    @Test
    @DisplayName("레이트리밋 키는 post:image:{userId}, 1분에 30장")
    void appliesPerUserRateLimit() {
        stubSaveAssigningId(1L);

        service.upload(USER_ID, file("x.png", "image/png", png(1, 1, 32)));

        assertThat(rateLimiter.keys).containsExactly("post:image:10");
        assertThat(rateLimiter.limit).isEqualTo(30);
        assertThat(rateLimiter.window).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("크기를 못 읽는 WebP 는 width·height 를 비운 채 저장한다")
    void storesWebpWithoutDimensions() {
        stubSaveAssigningId(7L);
        byte[] webp = new byte[32];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);

        PostImageView view = service.upload(USER_ID, file("x.webp", "image/webp", webp));

        assertThat(storage.key).endsWith(".webp");
        assertThat(storage.contentType).isEqualTo("image/webp");
        assertThat(view.width()).isNull();
        assertThat(view.height()).isNull();
    }

    @Test
    @DisplayName("저장소가 실패하면 그 예외를 그대로 올리고 행을 만들지 않는다")
    void doesNotSaveRowWhenStorageFails() {
        StorageService failing = new StorageService() {
            @Override
            public String store(String key, InputStream in, long size, String contentType) {
                throw ApiException.of(ErrorCode.STORAGE_ERROR);
            }

            @Override
            public void delete(String key) {
            }
        };
        PostImageService failingService =
                new PostImageService(imageRepository, failing, rateLimiter, properties(MAX_BYTES));

        assertThatThrownBy(() -> failingService.upload(USER_ID, file("x.png", "image/png", png(1, 1, 32))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORAGE_ERROR));
        verify(imageRepository, never()).save(any());
    }
}
