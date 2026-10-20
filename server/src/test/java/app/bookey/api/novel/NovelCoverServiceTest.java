package app.bookey.api.novel;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.NovelCover;
import app.bookey.domain.novel.NovelCoverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NovelCoverServiceTest {
    private final NovelCoverRepository covers = mock(NovelCoverRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final BookeyProperties properties = mock(BookeyProperties.class, RETURNS_DEEP_STUBS);
    private final NovelCoverService service = new NovelCoverService(covers, storage, properties, mock(RateLimiter.class));
    private final byte[] png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=");

    @BeforeEach void setup() {
        when(storage.enabled()).thenReturn(true);
        when(properties.storage().image().maxBytes()).thenReturn(10L * 1024 * 1024);
        when(storage.store(anyString(), any(), anyLong(), anyString())).thenReturn("https://storage/cover.png");
        when(covers.save(any())).thenAnswer(call -> {
            NovelCover cover = call.getArgument(0);
            ReflectionTestUtils.setField(cover, "id", 9L);
            return cover;
        });
    }

    @Test void generatedImageIsStoredWithOwnerAndDetectedDimensions() {
        var cover = service.storeGenerated(42L, png, "image/png");
        assertThat(cover.id()).isEqualTo(9L);
        assertThat(cover.width()).isEqualTo(1);
        assertThat(cover.height()).isEqualTo(1);
        verify(covers).save(argThat(value -> value.getUserId().equals(42L)
                && value.getNovelId() == null && value.getStorageKey().startsWith("novels/42/")));
    }

    @Test void invalidBytesOrMismatchedTypeAreNeverStored() {
        assertThatThrownBy(() -> service.storeGenerated(42L, "html".getBytes(), "image/png")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.storeGenerated(42L, png, "image/jpeg")).isInstanceOf(ApiException.class);
        verify(storage, never()).store(anyString(), any(), anyLong(), anyString());
    }

    @Test void databaseFailureDeletesTheGeneratedFile() {
        doThrow(new IllegalStateException("database unavailable")).when(covers).save(any());
        assertThatThrownBy(() -> service.storeGenerated(42L, png, "image/png")).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(startsWith("novels/42/"));
    }

    @Test void manualUploadStillUsesTheSameStoragePath() {
        var cover = service.upload(42L, new MockMultipartFile("file", "cover.png", "image/png", png));
        assertThat(cover.id()).isEqualTo(9L);
        assertThat(cover.url()).isEqualTo("https://storage/cover.png");
    }
}
