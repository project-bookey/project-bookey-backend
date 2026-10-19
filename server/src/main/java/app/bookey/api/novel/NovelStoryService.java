package app.bookey.api.novel;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.novel.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 이전 회차는 집필 권한을 확인한 작품/배달에서만 서버가 선택한다. */
@Service
@RequiredArgsConstructor
public class NovelStoryService {
    private final NovelWritingAssistService access;
    private final NovelRepository novels;
    private final NovelChapterRepository chapters;
    private final NovelDeliveryRepository deliveries;
    private final NovelAiClient ai;

    public enum Mode { CONTINUE, PLOT, OPENING }
    public record Request(@NotNull @Min(1) Long turnNumber, @NotNull Mode mode,
                          @NotNull @Size(max = 20000) String body,
                          @NotNull @Size(max = 300) String prompt) {}

    public NovelAiClient.Story turn(Long me, Long novelId, Request request) {
        access.authorizeTurn(me, novelId, request.turnNumber());
        Novel novel = novels.findById(novelId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        String previous = chapters.findFirstByNovelIdOrderByChapterNumberDescIdDesc(novelId)
                .map(NovelChapter::getBody).orElse("");
        return generate(novel, previous, request);
    }

    public NovelAiClient.Story branch(Long me, Long deliveryId, Request request) {
        access.authorizeBranch(me, deliveryId, request.turnNumber());
        NovelDelivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Novel novel = novels.findById(delivery.getNovelId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        String previous = chapters.findByIdAndNovelId(delivery.getChapterId(), delivery.getNovelId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)).getBody();
        return generate(novel, previous, request);
    }

    private NovelAiClient.Story generate(Novel novel, String previous, Request request) {
        if (request.mode() == Mode.OPENING && request.prompt().isBlank())
            throw new ApiException(ErrorCode.INVALID_REQUEST, "소재나 분위기를 적어 주세요.");
        if (request.body().isBlank() && previous.isBlank() && request.prompt().isBlank())
            throw new ApiException(ErrorCode.INVALID_REQUEST, "먼저 원고나 원하는 이야기 방향을 적어 주세요.");
        // 전체 원고를 외부로 보내지 않는다. 총 길이는 최대 2,000자 이내다.
        String context = "작품: " + head(novel.getTitle(), 80)
                + "\n장르: " + head(novel.getGenre(), 30)
                + "\n소재·분위기·희망 전개: " + request.prompt()
                + "\n앞 회차 끝부분:\n" + tail(previous, 500)
                + "\n작성 중인 원고 끝부분:\n" + tail(request.body(), 900);
        return ai.story(request.mode().name(), context);
    }

    private static String head(String text, int max) {
        int length = Math.min(max, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, length));
    }

    private static String tail(String text, int max) {
        int length = text.codePointCount(0, text.length());
        return text.substring(text.offsetByCodePoints(0, Math.max(0, length - max)));
    }
}
