package app.bookey.batch;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.domain.inquiry.InquiryImage;
import app.bookey.domain.inquiry.InquiryImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 고아 문의 사진 정리 — 기준 시각(24시간 전)과 조건부 삭제 경합, 파일 삭제 실패를 고정한다. */
class InquiryImageCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-10-03T19:40:00Z");

    private static final class RecordingStorage implements StorageService {
        final List<String> deleted = new ArrayList<>();
        final Set<String> failing;

        RecordingStorage(Set<String> failing) {
            this.failing = failing;
        }

        @Override
        public String store(String key, InputStream in, long size, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String key) {
            if (failing.contains(key)) {
                throw ApiException.of(ErrorCode.STORAGE_ERROR);
            }
            deleted.add(key);
        }
    }

    private final InquiryImageRepository imageRepository = mock(InquiryImageRepository.class);

    private static InquiryImage image(long id, String key) {
        InquiryImage image = InquiryImage.builder()
                .userId(10L).storageKey(key).url("http://x/" + key).contentType("image/png").byteSize(1)
                .build();
        try {
            Field f = InquiryImage.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(image, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return image;
    }

    @Test
    @DisplayName("24시간 넘게 떨어져 있던 사진을 조회하고, 정말 지워진 행만 파일을 지운다")
    void deletesOnlyRowsActuallyRemoved() {
        RecordingStorage storage = new RecordingStorage(Set.of());
        InquiryImageCleanupJob job = new InquiryImageCleanupJob(imageRepository, storage);
        InquiryImage gone = image(1L, "a.png");
        InquiryImage reattached = image(2L, "b.png");
        when(imageRepository.findAllByInquiryIdIsNullAndUpdatedAtBefore(NOW.minus(InquiryImageCleanupJob.ORPHAN_TTL)))
                .thenReturn(List.of(gone, reattached));
        when(imageRepository.deleteIfDetached(1L)).thenReturn(1);
        when(imageRepository.deleteIfDetached(2L)).thenReturn(0);

        int deleted = job.run(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(storage.deleted).containsExactly("a.png");
        verify(imageRepository).findAllByInquiryIdIsNullAndUpdatedAtBefore(Instant.parse("2026-10-02T19:40:00Z"));
    }

    @Test
    @DisplayName("파일 삭제가 실패해도 다음 사진으로 넘어간다 — 행은 이미 지웠으므로 되돌리지 않는다")
    void continuesWhenFileDeleteFails() {
        RecordingStorage storage = new RecordingStorage(Set.of("a.png"));
        InquiryImageCleanupJob job = new InquiryImageCleanupJob(imageRepository, storage);
        when(imageRepository.findAllByInquiryIdIsNullAndUpdatedAtBefore(NOW.minus(InquiryImageCleanupJob.ORPHAN_TTL)))
                .thenReturn(List.of(image(1L, "a.png"), image(2L, "b.png")));
        when(imageRepository.deleteIfDetached(1L)).thenReturn(1);
        when(imageRepository.deleteIfDetached(2L)).thenReturn(1);

        int deleted = job.run(NOW);

        assertThat(deleted).isEqualTo(2);
        assertThat(storage.deleted).containsExactly("b.png");
    }
}
