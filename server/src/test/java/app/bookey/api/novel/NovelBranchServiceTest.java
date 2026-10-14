package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelBranchServiceTest {
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelDraftRepository drafts = mock(NovelDraftRepository.class);
    private final NovelDeliveryRepository deliveries = mock(NovelDeliveryRepository.class);
    private final NovelDeliveryService delivery = mock(NovelDeliveryService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NovelBranchService service = new NovelBranchService(novels, chapters, drafts, deliveries, delivery,
            users, mock(RateLimiter.class));
    private Novel open;
    private NovelChapter parent;
    private NovelDelivery held;

    @BeforeEach void setup() {
        open = Novel.open(1L, "열매", "소개", "판타지", 20, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 10L);
        parent = new NovelChapter(10L, 1L, null, 1, "뿌리", "본문");
        ReflectionTestUtils.setField(parent, "id", 100L);
        held = new NovelDelivery(10L, 100L, 5L, Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(held, "id", 7L);
        when(novels.lockById(10L)).thenReturn(Optional.of(open));
        when(novels.findById(10L)).thenReturn(Optional.of(open));
        when(deliveries.findById(7L)).thenReturn(Optional.of(held));
        when(deliveries.lockById(7L)).thenReturn(Optional.of(held));
        when(chapters.findById(100L)).thenReturn(Optional.of(parent));
        when(chapters.saveAndFlush(any(NovelChapter.class))).thenAnswer(call -> {
            NovelChapter saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 200L);
            return saved;
        });
    }

    @Test @DisplayName("받은 회차에서 가지를 뻗으면 깊이가 하나 늘고, 새 회차가 다시 배달된다")
    void publishingBranchGrowsFromDeliveredChapter() {
        NovelChapterView branch = service.publish(5L, 7L, new NovelBranchWriteRequest("1_1", "이어 쓴 이야기"));
        assertThat(branch.parentChapterId()).isEqualTo(100L);
        assertThat(branch.chapterNumber()).isEqualTo(2);
        assertThat(held.getStatus()).isEqualTo(NovelDeliveryStatus.WRITTEN);
        assertThat(held.getWrittenChapterId()).isEqualTo(200L);
        assertThat(open.getChapterCount()).isEqualTo(1);
        verify(delivery).deliverNewChapter(eq(open), argThat(c -> c.getId() == 200L), any());
    }
    @Test @DisplayName("다른 사람이 받은 열매로는 쓸 수 없다")
    void othersCannotUseDelivery() {
        assertThatThrownBy(() -> service.publish(6L, 7L, new NovelBranchWriteRequest("제목", "본문")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verify(chapters, never()).saveAndFlush(any());
    }
    @Test @DisplayName("마감이 지난 열매나 이미 쓴 열매로는 다시 쓸 수 없다")
    void goneDeliveryCannotPublish() {
        ReflectionTestUtils.setField(held, "dueAt", Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.publish(5L, 7L, new NovelBranchWriteRequest("제목", "본문")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_DELIVERY_GONE);
        ReflectionTestUtils.setField(held, "dueAt", Instant.now().plusSeconds(60));
        held.written(300L);
        assertThatThrownBy(() -> service.publish(5L, 7L, new NovelBranchWriteRequest("제목", "본문")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_DELIVERY_GONE);
        verify(chapters, never()).saveAndFlush(any());
    }
    @Test @DisplayName("완결한 작품의 열매로는 쓸 수 없다")
    void completedNovelRejectsBranch() {
        open.complete();
        assertThatThrownBy(() -> service.publish(5L, 7L, new NovelBranchWriteRequest("제목", "본문")))
                .isInstanceOf(ApiException.class);
        verify(chapters, never()).saveAndFlush(any());
    }
    @Test @DisplayName("넘기면 열매를 내려놓고 다른 한 사람에게 다시 보낸다")
    void declineResends() {
        service.decline(5L, 7L);
        assertThat(held.getStatus()).isEqualTo(NovelDeliveryStatus.DECLINED);
        verify(delivery).resend(eq(open), eq(parent), any());
    }
    @Test @DisplayName("초안은 배달 id 로 저장해 다른 열매의 원고와 섞이지 않는다")
    void draftIsKeyedByDelivery() {
        NovelDraft stale = new NovelDraft(10L, 5L);
        stale.save(3L, "예전 열매", "예전 원고");
        when(drafts.findByNovelIdAndUserId(10L, 5L)).thenReturn(Optional.of(stale));
        assertThat(service.draft(5L, 7L).body()).isEmpty();
        NovelDraftView saved = service.saveDraft(5L, 7L, new NovelBranchWriteRequest("제목", "새 원고"));
        assertThat(saved.turnNumber()).isEqualTo(7L); assertThat(saved.body()).isEqualTo("새 원고");
    }
}
