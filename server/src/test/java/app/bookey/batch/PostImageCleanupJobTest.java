package app.bookey.batch;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.domain.post.PostImage;
import app.bookey.domain.post.PostImageRepository;
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
 * 고아 이미지 정리 정책 단위 테스트. 리포지토리는 Mockito 로 대신하되 조회는 실제 쿼리 조건
 * (post_id IS NULL AND created_at < ?) 을 인메모리로 흉내 내 잡이 넘기는 기준 시각까지 검증한다.
 * 조건부 삭제는 행 수(0/1)로 스텁해 조회~삭제 사이의 경합을 그대로 재현한다.
 */
class PostImageCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-09-03T04:20:00Z");

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

    private final PostImageRepository imageRepository = mock(PostImageRepository.class);

    private PostImage image(long id, String key, Long postId, Instant createdAt) {
        PostImage image = PostImage.builder()
                .userId(10L).storageKey(key).url("http://x/uploads/" + key).contentType("image/png")
                .byteSize(100).width(1).height(1)
                .build();
        set(image, "id", id);
        set(image, "createdAt", createdAt);
        if (postId != null) {
            image.attach(postId, 0);
        }
        return image;
    }

    /** id 는 엔티티 자신에, createdAt 은 BaseTimeEntity 에 있어 상위 클래스까지 올라가며 찾는다. */
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

    /** 리포지토리 쿼리 메서드의 의미(post_id IS NULL AND created_at < before)를 인메모리로 흉내 낸다. */
    private void stubOrphanQuery(List<PostImage> all) {
        when(imageRepository.findAllByPostIdIsNullAndCreatedAtBefore(any())).thenAnswer(invocation -> {
            Instant before = invocation.getArgument(0);
            return all.stream()
                    .filter(PostImage::isDetached)
                    .filter(image -> image.getCreatedAt().isBefore(before))
                    .toList();
        });
        stubStillDetached();
    }

    /** 기본값: 조회 때와 달라진 것이 없어 조건부 DELETE 가 한 행을 지운다. */
    private void stubStillDetached() {
        when(imageRepository.deleteIfDetached(anyLong())).thenReturn(1);
    }

    @Test
    @DisplayName("24시간이 지난 고아만 파일과 행을 지운다 — 붙은 사진·24시간 미만 고아는 건드리지 않는다")
    void deletesOnlyOrphansOlderThanOneDay() {
        PostImage staleOrphan = image(1L, "posts/10/2026/09/a.png", null, NOW.minus(Duration.ofHours(25)));
        PostImage freshOrphan = image(2L, "posts/10/2026/09/b.png", null, NOW.minus(Duration.ofHours(23)));
        PostImage attachedOld = image(3L, "posts/10/2026/09/c.png", 7L, NOW.minus(Duration.ofDays(30)));
        PostImage justAtBoundary = image(4L, "posts/10/2026/09/d.png", null, NOW.minus(Duration.ofHours(24)));
        stubOrphanQuery(List.of(staleOrphan, freshOrphan, attachedOld, justAtBoundary));
        RecordingStorage storage = new RecordingStorage(Set.of());
        PostImageCleanupJob job = new PostImageCleanupJob(imageRepository, storage);

        int deleted = job.run(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(storage.deleted).containsExactly("posts/10/2026/09/a.png");
        verify(imageRepository).deleteIfDetached(staleOrphan.getId());
        verify(imageRepository, never()).deleteIfDetached(freshOrphan.getId());
        verify(imageRepository, never()).deleteIfDetached(attachedOld.getId());
        // 정확히 24h 는 아직 '지난' 것이 아니다
        verify(imageRepository, never()).deleteIfDetached(justAtBoundary.getId());
    }

    @Test
    @DisplayName("조회 뒤 독후감에 붙은 사진은 행 삭제가 0 행이라 파일도 지우지 않는다")
    void keepsFileWhenRowWasAttachedInBetween() {
        PostImage raced = image(1L, "posts/10/2026/09/a.png", null, NOW.minus(Duration.ofDays(2)));
        PostImage plain = image(2L, "posts/10/2026/09/b.png", null, NOW.minus(Duration.ofDays(2)));
        stubOrphanQuery(List.of(raced, plain));
        // 목록을 읽은 뒤 사용자가 raced 를 독후감에 붙였다 — post_id IS NULL 조건이 걸려 0 행이 지워진다
        when(imageRepository.deleteIfDetached(raced.getId())).thenReturn(0);
        RecordingStorage storage = new RecordingStorage(Set.of());

        int deleted = new PostImageCleanupJob(imageRepository, storage).run(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(storage.deleted).containsExactly("posts/10/2026/09/b.png");   // 붙은 사진의 파일은 살아 있다
        verify(imageRepository).deleteIfDetached(raced.getId());
    }

    @Test
    @DisplayName("행을 지운 뒤 저장소 삭제가 실패해도 되돌리지 않고 나머지를 계속 처리한다")
    void deletesRowEvenWhenStorageDeleteFailsAndContinues() {
        PostImage first = image(1L, "posts/10/2026/09/a.png", null, NOW.minus(Duration.ofDays(2)));
        PostImage broken = image(2L, "posts/10/2026/09/b.png", null, NOW.minus(Duration.ofDays(2)));
        PostImage last = image(3L, "posts/10/2026/09/c.png", null, NOW.minus(Duration.ofDays(2)));
        stubOrphanQuery(List.of(first, broken, last));
        RecordingStorage storage = new RecordingStorage(Set.of("posts/10/2026/09/b.png"));
        PostImageCleanupJob job = new PostImageCleanupJob(imageRepository, storage);

        int deleted = job.run(NOW);

        assertThat(deleted).isEqualTo(3);
        assertThat(storage.deleted).containsExactly("posts/10/2026/09/a.png", "posts/10/2026/09/c.png");
        verify(imageRepository).deleteIfDetached(first.getId());
        verify(imageRepository).deleteIfDetached(broken.getId());
        verify(imageRepository).deleteIfDetached(last.getId());
    }

    @Test
    @DisplayName("행 삭제가 예외로 실패하면 그 파일은 건드리지 않고 나머지는 계속 처리한다")
    void continuesWhenRowDeleteFails() {
        PostImage first = image(1L, "posts/10/2026/09/a.png", null, NOW.minus(Duration.ofDays(2)));
        PostImage last = image(2L, "posts/10/2026/09/b.png", null, NOW.minus(Duration.ofDays(2)));
        stubOrphanQuery(List.of(first, last));
        when(imageRepository.deleteIfDetached(first.getId())).thenThrow(new RuntimeException("DB 끊김"));
        RecordingStorage storage = new RecordingStorage(Set.of());
        PostImageCleanupJob job = new PostImageCleanupJob(imageRepository, storage);

        int deleted = job.run(NOW);

        assertThat(deleted).isEqualTo(1);
        // 행이 남았으므로 파일도 남겨야 다음 실행에서 다시 회수된다
        assertThat(storage.deleted).containsExactly("posts/10/2026/09/b.png");
        verify(imageRepository).deleteIfDetached(last.getId());
    }

    @Test
    @DisplayName("고아가 없으면 아무것도 지우지 않는다")
    void doesNothingWhenNoOrphans() {
        stubOrphanQuery(List.of(image(1L, "posts/10/2026/09/a.png", 7L, NOW.minus(Duration.ofDays(9)))));
        RecordingStorage storage = new RecordingStorage(Set.of());

        int deleted = new PostImageCleanupJob(imageRepository, storage).run(NOW);

        assertThat(deleted).isZero();
        assertThat(storage.deleted).isEmpty();
        verify(imageRepository, never()).deleteIfDetached(anyLong());
    }
}
