package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.NovelDraftView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.RateLimiter;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelWritingAssistServiceTest {
    private final NovelService novels = mock(NovelService.class);
    private final NovelBranchService branches = mock(NovelBranchService.class);
    private final NovelAiClient ai = mock(NovelAiClient.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final NovelWritingAssistService service = new NovelWritingAssistService(novels, branches, ai, limiter);
    private final NovelWritingAssistService.Request request = new NovelWritingAssistService.Request(3L, "원고");

    @BeforeEach void setUp() {
        when(limiter.tryAcquire("novel:ai:day:1", 10, Duration.ofDays(1))).thenReturn(true);
        when(ai.rewrite("원고")).thenReturn(new NovelAiClient.Rewrite("다듬은 원고"));
    }

    @Test void turnChecksExistingWritePermissionAndDoesNotSaveDraft() {
        when(novels.draft(1L, 7L)).thenReturn(new NovelDraftView(3L, "", "", null));
        assertThat(service.turn(1L, 7L, request).text()).isEqualTo("다듬은 원고");
        verify(novels).draft(1L, 7L);
        verifyNoMoreInteractions(novels);
        verify(ai).rewrite("원고");
    }

    @Test void branchUsesDeliveryOwnershipAndExpiryChecks() {
        when(branches.draft(1L, 3L)).thenReturn(new NovelDraftView(3L, "", "", null));
        assertThat(service.branch(1L, 3L, request).text()).isEqualTo("다듬은 원고");
        verify(branches).draft(1L, 3L);
        verifyNoMoreInteractions(branches);
    }

    @Test void staleTurnNeverCallsAiOrConsumesQuota() {
        when(novels.draft(1L, 7L)).thenReturn(new NovelDraftView(4L, "", "", null));
        assertThatThrownBy(() -> service.turn(1L, 7L, request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(ai, limiter);
    }

    @Test void expiredOrSomeoneElsesDeliveryNeverCallsAi() {
        when(branches.draft(1L, 3L)).thenThrow(ApiException.of(ErrorCode.NOVEL_DELIVERY_GONE));
        assertThatThrownBy(() -> service.branch(1L, 3L, request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(ai, limiter);
    }

    @Test void dailyLimitIsSharedAcrossNovelsAndBranches() {
        when(novels.draft(1L, 7L)).thenReturn(new NovelDraftView(3L, "", "", null));
        when(limiter.tryAcquire("novel:ai:day:1", 10, Duration.ofDays(1))).thenReturn(false);
        assertThatThrownBy(() -> service.turn(1L, 7L, request))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_DAILY_LIMIT));
        verifyNoInteractions(ai);
    }

    @Test void requestRejectsBlankOversizedTextAndMissingTurn() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request)).isEmpty();
            assertThat(validator.validate(new NovelWritingAssistService.Request(3L, " "))).isNotEmpty();
            assertThat(validator.validate(new NovelWritingAssistService.Request(3L, "가".repeat(2001)))).isNotEmpty();
            assertThat(validator.validate(new NovelWritingAssistService.Request(null, "원고"))).isNotEmpty();
        }
    }
}
