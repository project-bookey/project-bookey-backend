package app.bookey.api.inquiry;

import app.bookey.api.inquiry.dto.InquiryDtos.InquiryImageView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.inquiry.InquiryImage;
import app.bookey.domain.inquiry.InquiryImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
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

/** PostImageServiceTest 와 같은 구성 — 문의 사진에서 달라지는 키·레이트리밋·보상 삭제를 고정한다. */
class InquiryImageServiceTest {

    private static final long USER_ID = 10L;
    private static final long MAX_BYTES = 1024;

    private static final class RecordingStorage implements StorageService {
        String key;
        int storeCalls;
        final List<String> deletedKeys = new ArrayList<>();
        boolean enabled = true;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public String store(String key, InputStream in, long size, String contentType) {
            storeCalls++;
            this.key = key;
            try {
                in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return "http://localhost:8098/uploads/" + key;
        }

        @Override
        public void delete(String key) {
            deletedKeys.add(key);
        }
    }

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

    private final InquiryImageRepository imageRepository = mock(InquiryImageRepository.class);
    private final RecordingStorage storage = new RecordingStorage();
    private final RecordingRateLimiter rateLimiter = new RecordingRateLimiter();
    private final InquiryImageService service =
            new InquiryImageService(imageRepository, storage, rateLimiter, properties());

    private static BookeyProperties properties() {
        return new BookeyProperties(null, null, null, null, null, null, null, null, new BookeyProperties.Storage("local",
                new BookeyProperties.Storage.Local("./uploads", ""),
                new BookeyProperties.Storage.Gcs(""),
                new BookeyProperties.Storage.S3("", "ap-northeast-2", ""),
                new BookeyProperties.Storage.Image(MAX_BYTES, 10)), null);
    }

    private static byte[] png(int totalLength) {
        byte[] bytes = new byte[Math.max(totalLength, 24)];
        byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52};
        System.arraycopy(header, 0, bytes, 0, header.length);
        bytes[19] = 2;  // width 2
        bytes[23] = 3;  // height 3
        return bytes;
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "shot.png", "image/png", content);
    }

    private void stubSaveAssigningId(long id) {
        when(imageRepository.save(any())).thenAnswer(invocation -> {
            InquiryImage image = invocation.getArgument(0);
            Field f = InquiryImage.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(image, id);
            return image;
        });
    }

    @Test
    @DisplayName("정상 PNG — inquiries/{userId}/yyyy/MM/{uuid}.png 키로 저장하고 임시(문의 없음) 행을 만든다")
    void storesUnderInquiryKey() {
        stubSaveAssigningId(42L);

        InquiryImageView view = service.upload(USER_ID, file(png(100)));

        assertThat(storage.key).matches("inquiries/10/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        ArgumentCaptor<InquiryImage> saved = ArgumentCaptor.forClass(InquiryImage.class);
        verify(imageRepository).save(saved.capture());
        assertThat(saved.getValue().isDetached()).isTrue();
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.width()).isEqualTo(2);
        assertThat(view.height()).isEqualTo(3);
    }

    @Test
    @DisplayName("레이트리밋 키는 inquiry:image:{userId}, 1분에 10장")
    void appliesPerUserRateLimit() {
        stubSaveAssigningId(1L);

        service.upload(USER_ID, file(png(32)));

        assertThat(rateLimiter.keys).containsExactly("inquiry:image:10");
        assertThat(rateLimiter.limit).isEqualTo(10);
        assertThat(rateLimiter.window).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("크기 제한을 넘으면 IMAGE_TOO_LARGE — 저장소·DB 를 건드리지 않는다")
    void rejectsOversizedFile() {
        assertThatThrownBy(() -> service.upload(USER_ID, file(png((int) MAX_BYTES + 1))))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
        assertThat(storage.storeCalls).isZero();
        verify(imageRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장소가 꺼져 있으면 가장 먼저 STORAGE_DISABLED — 레이트리밋도 쓰지 않는다")
    void rejectsWhenStorageDisabled() {
        storage.enabled = false;

        assertThatThrownBy(() -> service.upload(USER_ID, file(png(32))))
                .extracting("errorCode").isEqualTo(ErrorCode.STORAGE_DISABLED);
        assertThat(rateLimiter.keys).isEmpty();
    }

    @Test
    @DisplayName("행 저장이 실패하면 올린 파일을 지운다 — 행 없는 파일은 정리 배치도 못 찾는다")
    void deletesStoredFileWhenRowSaveFails() {
        when(imageRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.upload(USER_ID, file(png(32))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.deletedKeys).containsExactly(storage.key);
    }
}
