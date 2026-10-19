package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelDraftView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 집필 권한 확인은 기존 초안 서비스를 통과하고, DB 트랜잭션을 끝낸 뒤 AI를 호출한다. */
@Service
@RequiredArgsConstructor
public class NovelWritingAssistService {
    private final NovelService novels;
    private final NovelBranchService branches;
    private final NovelAiClient ai;
    private final RateLimiter limiter;

    public record Request(@NotNull @Min(1) Long turnNumber, @NotBlank @Size(max = 2000) String text) {}

    public NovelAiClient.Rewrite turn(Long me, Long novelId, Request request) {
        return rewrite(me, novels.draft(me, novelId), request);
    }

    public NovelAiClient.Rewrite branch(Long me, Long deliveryId, Request request) {
        return rewrite(me, branches.draft(me, deliveryId), request);
    }

    private NovelAiClient.Rewrite rewrite(Long me, NovelDraftView draft, Request request) {
        if (!request.turnNumber().equals(draft.turnNumber()))
            throw ApiException.of(ErrorCode.NOVEL_NOT_YOUR_TURN);
        limiter.require("novel:ai:minute:" + me, 3, Duration.ofMinutes(1));
        if (!limiter.tryAcquire("novel:ai:day:" + me, 10, Duration.ofDays(1)))
            throw ApiException.of(ErrorCode.AI_DAILY_LIMIT);
        return ai.rewrite(request.text());
    }
}
