package app.bookey.api.novel;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.novel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelStoryServiceTest {
    private final NovelWritingAssistService access = mock(NovelWritingAssistService.class);
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelDeliveryRepository deliveries = mock(NovelDeliveryRepository.class);
    private final NovelAiClient ai = mock(NovelAiClient.class);
    private final NovelStoryService service = new NovelStoryService(access, novels, chapters, deliveries, ai);
    private final Novel novel = new Novel(1L, NovelKind.RELAY, "달빛 숲", "", "판타지", 2, 20, 24, true);

    @BeforeEach void setUp() {
        when(novels.findById(7L)).thenReturn(Optional.of(novel));
        when(chapters.findFirstByNovelIdOrderByChapterNumberDescIdDesc(7L)).thenReturn(Optional.empty());
        when(ai.story(anyString(), anyString())).thenReturn(new NovelAiClient.Story(
                List.of(new NovelAiClient.StoryOption("달빛", "작은 문이 열렸다."))));
    }

    private NovelStoryService.Request request(NovelStoryService.Mode mode, String body, String prompt) {
        return new NovelStoryService.Request(3L, mode, body, prompt);
    }

    @Test void permissionIsCheckedBeforeReadingAnyPreviousChapterOrCallingAi() {
        doThrow(ApiException.of(ErrorCode.NOVEL_NOT_YOUR_TURN)).when(access).authorizeTurn(1L, 7L, 3L);
        assertThatThrownBy(() -> service.turn(1L, 7L, request(NovelStoryService.Mode.CONTINUE, "원고", "")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(novels, chapters, deliveries, ai);
    }

    @Test void branchUsesExactlyTheDeliveredParentAndSharedWritingQuota() {
        when(deliveries.findById(3L)).thenReturn(Optional.of(new NovelDelivery(7L, 21L, 1L, Instant.MAX)));
        when(chapters.findByIdAndNovelId(21L, 7L)).thenReturn(Optional.of(new NovelChapter(7L, 2L, null, 1, "앞회차", "내가 이어받은 문장")));
        service.branch(1L, 3L, request(NovelStoryService.Mode.PLOT, "지금 원고", "긴장감 있게"));
        verify(access).authorizeBranch(1L, 3L, 3L);
        verify(ai).story(eq("PLOT"), contains("내가 이어받은 문장"));
        verify(chapters, never()).findFirstByNovelIdOrderByChapterNumberDescIdDesc(anyLong());
    }

    @Test void onlyBoundedTailsAreSentAndUnicodeCharactersAreNotSplit() {
        String previous = "비공개 앞부분" + "🌙".repeat(4000) + "앞회차 마지막";
        when(chapters.findFirstByNovelIdOrderByChapterNumberDescIdDesc(7L))
                .thenReturn(Optional.of(new NovelChapter(7L, 2L, null, 1, "앞회차", previous)));
        service.turn(1L, 7L, request(NovelStoryService.Mode.CONTINUE, "제외할 앞부분" + "가".repeat(19000) + "최신 원고", "잔잔하게"));
        var context = ArgumentCaptor.forClass(String.class);
        verify(ai).story(eq("CONTINUE"), context.capture());
        String sent = context.getValue();
        assertThat(sent.codePointCount(0, sent.length())).isLessThanOrEqualTo(2000);
        assertThat(sent).contains("달빛 숲", "판타지", "앞회차 마지막", "최신 원고", "잔잔하게")
                .doesNotContain("비공개 앞부분", "제외할 앞부분");
        assertThat(sent.codePoints().anyMatch(code -> code >= 0xD800 && code <= 0xDFFF)).isFalse();
    }

    @Test void firstParagraphRequiresMaterialAndDoesNotSaveAnyDraft() {
        assertThatThrownBy(() -> service.turn(1L, 7L, request(NovelStoryService.Mode.OPENING, "", "")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(ai);
        service.turn(1L, 7L, request(NovelStoryService.Mode.OPENING, "", "비 오는 밤의 편지"));
        verify(ai).story(eq("OPENING"), contains("비 오는 밤의 편지"));
        verify(novels, never()).save(any());
        verify(chapters, never()).save(any());
    }
}
