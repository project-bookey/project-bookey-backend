package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelCoverView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NovelCoverGenerationServiceTest {
    private final NovelCoverAiClient ai = mock(NovelCoverAiClient.class);
    private final NovelCoverService covers = mock(NovelCoverService.class);
    private final StorageService storage = mock(StorageService.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final NovelCoverGenerationService service = new NovelCoverGenerationService(ai, covers, storage, limiter, 3, 100);
    private final NovelCoverGenerationService.NovelCoverGenerationRequest request = new NovelCoverGenerationService.NovelCoverGenerationRequest(" 달빛 ", " 밤의 가게 ", "판타지");

    @BeforeEach void setup() {
        when(storage.enabled()).thenReturn(true);
        when(ai.available()).thenReturn(true);
        when(limiter.acquire(anyString(), anyInt(), any())).thenReturn(new RateLimiter.Permit(true, 0));
    }

    @Test void savesGeneratedCoverWithCurrentOwner() {
        byte[] image = {1, 2, 3};
        when(ai.generate(any())).thenReturn(new NovelCoverAiClient.GeneratedCoverImage(image, "image/png"));
        var cover = new NovelCoverView(8L, "https://storage/cover.png", 768, 1024);
        when(covers.storeGenerated(42L, image, "image/png")).thenReturn(cover);
        assertThat(service.generate(42L, request)).isEqualTo(cover);
        verify(ai).generate(new NovelCoverGenerationService.NovelCoverGenerationRequest("달빛", "밤의 가게", "판타지"));
    }

    @Test void noPaidRequestWhenStorageIsDisabled() {
        when(storage.enabled()).thenReturn(false);
        assertThat(service.config().available()).isFalse();
        assertThatThrownBy(() -> service.generate(42L, request)).isInstanceOf(ApiException.class);
        verify(ai, never()).generate(any());
    }

    @Test void noPaidRequestWhenQuotaIsExceeded() {
        when(limiter.acquire(eq("novel:ai-cover:daily:42"), anyInt(), any()))
                .thenReturn(new RateLimiter.Permit(false, 0));
        assertThatThrownBy(() -> service.generate(42L, request)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_COVER_LIMIT));
        verify(ai, never()).generate(any());
    }

    @Test void noPaidRequestWhenRedisIsUnavailable() {
        when(limiter.acquire(anyString(), anyInt(), any())).thenReturn(new RateLimiter.Permit(true, null));
        assertThatThrownBy(() -> service.generate(42L, request)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_COVER_UNAVAILABLE));
        verify(ai, never()).generate(any());
    }
}
