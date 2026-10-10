package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelServiceTest {
    private final NovelRepository novels = mock(NovelRepository.class);
    private final NovelMemberRepository members = mock(NovelMemberRepository.class);
    private final NovelChapterRepository chapters = mock(NovelChapterRepository.class);
    private final NovelDraftRepository drafts = mock(NovelDraftRepository.class);
    private final NovelCoverRepository covers = mock(NovelCoverRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NovelService service = new NovelService(novels, members, chapters, drafts, covers, users, mock(RateLimiter.class));
    private Novel n;
    @BeforeEach void setup() {
        n = new Novel(1L, NovelKind.RELAY, "제목", "소개", "일상", 2, 10, 24, true, "code");
        ReflectionTestUtils.setField(n, "id", 10L); ReflectionTestUtils.setField(n, "createdAt", Instant.now());
        when(novels.lockById(10L)).thenReturn(Optional.of(n)); when(novels.findById(10L)).thenReturn(Optional.of(n));
        when(members.activeMembers(10L)).thenReturn(List.of(new NovelMember(10L, 1L, true)));
    }
    @Test void outsiderCannotApproveApplication() {
        assertThatThrownBy(() -> service.decide(3L, 10L, 2L, true)).isInstanceOf(ApiException.class);
        verify(members, never()).findByNovelIdAndUserId(10L, 2L);
    }
    @Test void approvalDoesNotExceedCapacity() {
        when(members.activeMembers(10L)).thenReturn(List.of(new NovelMember(10L, 1L, true), new NovelMember(10L, 3L, true)));
        NovelMember applicant = new NovelMember(10L, 2L, false);
        when(members.findByNovelIdAndUserId(10L, 2L)).thenReturn(Optional.of(applicant));
        assertThatThrownBy(() -> service.decide(1L, 10L, 2L, true)).isInstanceOf(ApiException.class);
        assertThat(applicant.getStatus()).isEqualTo("PENDING");
    }
    @Test void approvalAddsApplicantToWriters() {
        NovelMember applicant = new NovelMember(10L, 2L, false);
        when(members.findByNovelIdAndUserId(10L, 2L)).thenReturn(Optional.of(applicant));
        service.decide(1L, 10L, 2L, true);
        assertThat(applicant.getStatus()).isEqualTo("ACTIVE");
    }
    @Test void staleTurnCannotPublishDuplicateChapter() {
        n.start(Instant.now());
        assertThatThrownBy(() -> service.publish(1L, 10L, new NovelWriteRequest(2L, "회차", "본문"))).isInstanceOf(ApiException.class);
        verify(chapters, never()).saveAndFlush(any());
    }
    @Test void pendingParticipantCannotWrite() {
        n.start(Instant.now());
        assertThatThrownBy(() -> service.saveDraft(2L, 10L, new NovelWriteRequest(1L, "회차", "본문"))).isInstanceOf(ApiException.class);
        verifyNoInteractions(drafts);
    }
    @Test void cannotUseAnotherUsersCover() {
        NovelCover c = new NovelCover(2L, "novels/2/cover.jpg", "url", 100, 150);
        when(covers.lockById(5L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.changeCover(1L, 10L, 5L)).isInstanceOf(ApiException.class);
        assertThat(n.getCoverId()).isNull(); assertThat(c.getNovelId()).isNull();
    }
    @Test void pendingPrivateParticipantCannotReadChapters() {
        ReflectionTestUtils.setField(n, "isPublic", false);
        when(members.findByNovelIdAndUserId(10L, 2L)).thenReturn(Optional.of(new NovelMember(10L, 2L, false)));
        assertThatThrownBy(() -> service.chapters(2L, 10L, PageRequest.of(0, 20))).isInstanceOf(ApiException.class);
        verifyNoInteractions(chapters);
    }
    @Test void privateNovelIsNotReadableWithoutMembership() {
        ReflectionTestUtils.setField(n, "isPublic", false);
        assertThatThrownBy(() -> service.get(2L, 10L)).isInstanceOf(ApiException.class);
    }
    @Test void cannotStartWithoutApprovedParticipant() {
        assertThatThrownBy(() -> service.start(1L, 10L)).isInstanceOf(ApiException.class);
        assertThat(n.getStatus()).isEqualTo(NovelStatus.RECRUITING);
    }
}
