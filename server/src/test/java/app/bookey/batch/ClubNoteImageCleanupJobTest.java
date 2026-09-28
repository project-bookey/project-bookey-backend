package app.bookey.batch;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.domain.club.ClubNoteImage;
import app.bookey.domain.club.ClubNoteImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 노트북 고아 사진 정리 정책 단위 테스트 — PostImageCleanupJobTest 와 같은 구조.
 * 조회 조건(page_id IS NULL AND updated_at < ?)을 인메모리로 흉내 내고, 조건부 삭제는 행 수(0/1)로 스텁해 경합을 재현한다.
 */
class ClubNoteImageCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-09-28T04:30:00Z");

    /** 지운 키를 기록하고, 지정한 키에서는 저장소 예외를 던지는 페이크. */
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

    private final ClubNoteImageRepository imageRepository = mock(ClubNoteImageRepository.class);

    private ClubNoteImage image(long id, String key, Long pageId, Instant updatedAt) {
        ClubNoteImage image = ClubNoteImage.builder()
                .clubId(10L).userId(1L).storageKey(key).url("http://x/uploads/" + key).contentType("image/png")
                .byteSize(100).width(1).height(1)
                .build();
        set(image, "id", id);
        set(image, "updatedAt", updatedAt);
        if (pageId != null) {
            image.attach(pageId);
        }
        return image;
    }

    /** id 는 엔티티 자신에, updatedAt 은 BaseTimeEntity 에 있어 상위 클래스까지 올라가며 찾는다. */
    private static void set(Object target, String field, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 리포지토리 쿼리(page_id IS NULL AND updated_at < before)를 인메모리로 흉내 낸다. */
    private void stubOrphanQuery(List<ClubNoteImage> all) {
        when(imageRepository.findAllByPageIdIsNullAndUpdatedAtBefore(any())).thenAnswer(invocation -> {
            Instant before = invocation.getArgument(0);
            return all.stream()
                    .filter(ClubNoteImage::isDetached)
                    .filter(image -> image.getUpdatedAt().isBefore(before))
                    .toList();
        });
        when(imageRepository.deleteIfDetached(anyLong())).thenReturn(1);
    }

    @Test
    @DisplayName("떼어진 지 24시간이 지난 고아만 파일과 행을 지운다 — 붙은 사진·24시간 미만 고아는 건드리지 않는다")
    void deletesOnlyOrphansOlderThanOneDay() {
        ClubNoteImage staleOrphan = image(1L, "clubs/10/notebook/1/2026/09/a.png", null, NOW.minus(Duration.ofHours(25)));
        ClubNoteImage freshOrphan = image(2L, "clubs/10/notebook/1/2026/09/b.png", null, NOW.minus(Duration.ofHours(23)));
        ClubNoteImage attachedOld = image(3L, "clubs/10/notebook/1/2026/09/c.png", 7L, NOW.minus(Duration.ofDays(30)));
        ClubNoteImage justAtBoundary = image(4L, "clubs/10/notebook/1/2026/09/d.png", null, NOW.minus(Duration.ofHours(24)));
        stubOrphanQuery(List.of(staleOrphan, freshOrphan, attachedOld, justAtBoundary));
        RecordingStorage storage = new RecordingStorage(Set.of());

        int deleted = new ClubNoteImageCleanupJob(imageRepository, storage).run(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(storage.deleted).containsExactly("clubs/10/notebook/1/2026/09/a.png");
        verify(imageRepository).deleteIfDetached(staleOrphan.getId());
        verify(imageRepository, never()).deleteIfDetached(freshOrphan.getId());
        verify(imageRepository, never()).deleteIfDetached(attachedOld.getId());
        verify(imageRepository, never()).deleteIfDetached(justAtBoundary.getId());
    }

    @Test
    @DisplayName("조회 뒤 문서에 붙은 사진은 행 삭제가 0 행이라 파일도 지우지 않는다")
    void keepsFileWhenRowWasAttachedInBetween() {
        ClubNoteImage raced = image(1L, "clubs/10/notebook/1/2026/09/a.png", null, NOW.minus(Duration.ofDays(2)));
        ClubNoteImage plain = image(2L, "clubs/10/notebook/1/2026/09/b.png", null, NOW.minus(Duration.ofDays(2)));
        stubOrphanQuery(List.of(raced, plain));
        when(imageRepository.deleteIfDetached(raced.getId())).thenReturn(0);
        RecordingStorage storage = new RecordingStorage(Set.of());

        int deleted = new ClubNoteImageCleanupJob(imageRepository, storage).run(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(storage.deleted).containsExactly("clubs/10/notebook/1/2026/09/b.png");
    }

    @Test
    @DisplayName("행을 지운 뒤 저장소 삭제가 실패해도 되돌리지 않고 나머지를 계속 처리한다")
    void continuesWhenStorageDeleteFails() {
        ClubNoteImage first = image(1L, "clubs/10/notebook/1/2026/09/a.png", null, NOW.minus(Duration.ofDays(2)));
        ClubNoteImage broken = image(2L, "clubs/10/notebook/1/2026/09/b.png", null, NOW.minus(Duration.ofDays(2)));
        ClubNoteImage last = image(3L, "clubs/10/notebook/1/2026/09/c.png", null, NOW.minus(Duration.ofDays(2)));
        stubOrphanQuery(List.of(first, broken, last));
        RecordingStorage storage = new RecordingStorage(Set.of("clubs/10/notebook/1/2026/09/b.png"));

        int deleted = new ClubNoteImageCleanupJob(imageRepository, storage).run(NOW);

        assertThat(deleted).isEqualTo(3);
        assertThat(storage.deleted).containsExactly("clubs/10/notebook/1/2026/09/a.png", "clubs/10/notebook/1/2026/09/c.png");
    }

    @Test
    @DisplayName("행 삭제가 예외로 실패하면 그 파일은 남기고 나머지는 계속 처리한다")
    void continuesWhenRowDeleteFails() {
        ClubNoteImage first = image(1L, "clubs/10/notebook/1/2026/09/a.png", null, NOW.minus(Duration.ofDays(2)));
        ClubNoteImage last = image(2L, "clubs/10/notebook/1/2026/09/b.png", null, NOW.minus(Duration.ofDays(2)));
        stubOrphanQuery(List.of(first, last));
        when(imageRepository.deleteIfDetached(first.getId())).thenThrow(new RuntimeException("DB 끊김"));
        RecordingStorage storage = new RecordingStorage(Set.of());

        int deleted = new ClubNoteImageCleanupJob(imageRepository, storage).run(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(storage.deleted).containsExactly("clubs/10/notebook/1/2026/09/b.png");
    }

    @Test
    @DisplayName("고아가 없으면 아무것도 지우지 않는다")
    void doesNothingWhenNoOrphans() {
        stubOrphanQuery(List.of(image(1L, "clubs/10/notebook/1/2026/09/a.png", 7L, NOW.minus(Duration.ofDays(9)))));
        RecordingStorage storage = new RecordingStorage(Set.of());

        int deleted = new ClubNoteImageCleanupJob(imageRepository, storage).run(NOW);

        assertThat(deleted).isZero();
        assertThat(storage.deleted).isEmpty();
        verify(imageRepository, never()).deleteIfDetached(anyLong());
    }
}
