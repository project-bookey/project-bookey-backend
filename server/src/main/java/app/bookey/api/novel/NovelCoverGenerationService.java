package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelCoverView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class NovelCoverGenerationService {
    private final NovelCoverAiClient ai;
    private final NovelCoverService covers;
    private final StorageService storage;
    private final RateLimiter limiter;
    private final int dailyLimit;
    private final int globalDailyLimit;

    public NovelCoverGenerationService(NovelCoverAiClient ai, NovelCoverService covers,
            StorageService storage, RateLimiter limiter,
            @Value("${bookey.ai.cover-daily-limit:3}") int dailyLimit,
            @Value("${bookey.ai.cover-global-daily-limit:100}") int globalDailyLimit) {
        this.ai = ai;
        this.covers = covers;
        this.storage = storage;
        this.limiter = limiter;
        this.dailyLimit = dailyLimit;
        this.globalDailyLimit = globalDailyLimit;
    }

    public record NovelCoverGenerationRequest(@NotBlank @Size(max = 120) String title,
                          @NotBlank @Size(max = 500) String description,
                          @NotBlank @Size(max = 30) String genre) {}
    public record NovelCoverGenerationConfig(boolean available, int dailyLimit) {}

    public NovelCoverGenerationConfig config() {
        return new NovelCoverGenerationConfig(storage.enabled() && dailyLimit > 0 && globalDailyLimit > 0 && ai.available(),
                Math.max(0, dailyLimit));
    }

    public NovelCoverView generate(Long me, NovelCoverGenerationRequest request) {
        if (!storage.enabled()) throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        if (dailyLimit <= 0 || globalDailyLimit <= 0 || !ai.available())
            throw ApiException.of(ErrorCode.AI_COVER_UNAVAILABLE);
        // 유료 호출은 제한 저장소가 고장 났을 때 허용하지 않는다. 실패한 시도도 횟수에 포함된다.
        require("novel:ai-cover:minute:" + me, 1, Duration.ofMinutes(1));
        require("novel:ai-cover:daily:" + me, dailyLimit, Duration.ofDays(1));
        require("novel:ai-cover:global", globalDailyLimit, Duration.ofDays(1));
        NovelCoverAiClient.GeneratedCoverImage image = ai.generate(new NovelCoverGenerationRequest(
                request.title().strip(), request.description().strip(), request.genre().strip()));
        return covers.storeGenerated(me, image.data(), image.contentType());
    }

    private void require(String key, int limit, Duration duration) {
        RateLimiter.Permit permit = limiter.acquire(key, limit, duration);
        if (permit.remaining() == null) throw ApiException.of(ErrorCode.AI_COVER_UNAVAILABLE);
        if (!permit.allowed()) throw ApiException.of(ErrorCode.AI_COVER_LIMIT);
    }
}
