package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.novel.*;
import app.bookey.domain.social.UserFollow;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
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
    private final NovelLikeRepository likes = mock(NovelLikeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NovelDeliveryRepository deliveries = mock(NovelDeliveryRepository.class);
    private final NovelDeliveryService delivery = mock(NovelDeliveryService.class);
    private final NovelBaseWorkRepository baseWorks = mock(NovelBaseWorkRepository.class);
    private final UserFollowRepository follows = mock(UserFollowRepository.class);
    private final NovelService service = new NovelService(novels, members, chapters, drafts, covers, likes, users,
            mock(RateLimiter.class), deliveries, delivery, baseWorks, follows);
    private Novel n;
    @BeforeEach void setup() {
        n = new Novel(1L, NovelKind.RELAY, "제목", "소개", "일상", 2, 10, 24, true);
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
    @Test @DisplayName("좋아요는 누를 때마다 켜졌다 꺼지고, 탈퇴자를 뺀 서버의 수를 돌려준다")
    void likeTogglesOnAndOff() {
        when(likes.countByNovelId(10L)).thenReturn(1L);
        NovelLikeView liked = service.toggleLike(2L, 10L);
        assertThat(liked.liked()).isTrue(); assertThat(liked.likeCount()).isEqualTo(1L);
        verify(likes).save(any(NovelLike.class));

        NovelLike existing = NovelLike.builder().userId(2L).novelId(10L).build();
        when(likes.findByUserIdAndNovelId(2L, 10L)).thenReturn(Optional.of(existing));
        when(likes.countByNovelId(10L)).thenReturn(0L);
        NovelLikeView unliked = service.toggleLike(2L, 10L);
        assertThat(unliked.liked()).isFalse(); assertThat(unliked.likeCount()).isZero();
        verify(likes).delete(existing);
    }
    @Test @DisplayName("볼 수 없는 비공개 작품에는 좋아요를 누를 수 없다")
    void cannotLikeUnreadablePrivateNovel() {
        ReflectionTestUtils.setField(n, "isPublic", false);
        assertThatThrownBy(() -> service.toggleLike(2L, 10L)).isInstanceOf(ApiException.class);
        verify(likes, never()).save(any());
    }
    @Test @DisplayName("목록은 한 페이지의 좋아요 수와 내가 누른 작품을 한 번에 읽어 싣는다")
    void feedCarriesLikesLoadedOnce() {
        Novel other = new Novel(1L, NovelKind.RELAY, "다른 제목", "소개", "일상", 2, 10, 24, true);
        ReflectionTestUtils.setField(other, "id", 11L); ReflectionTestUtils.setField(other, "createdAt", Instant.now());
        var page = PageRequest.of(0, 20);
        when(novels.feed(NovelKind.RELAY, null, page)).thenReturn(new PageImpl<>(List.of(n, other), page, 2));
        when(likes.countPerNovel(List.of(10L, 11L))).thenReturn(List.of(likeCount(10L, 128L)));
        when(likes.findAllByUserIdAndNovelIdIn(2L, List.of(10L, 11L)))
                .thenReturn(List.of(NovelLike.builder().userId(2L).novelId(10L).build()));

        var content = service.feed(2L, NovelKind.RELAY, null, page).content();

        assertThat(content.get(0).likeCount()).isEqualTo(128L); assertThat(content.get(0).liked()).isTrue();
        assertThat(content.get(1).likeCount()).isZero(); assertThat(content.get(1).liked()).isFalse();
        verify(likes, times(1)).countPerNovel(any()); verify(likes, never()).countByNovelId(any());
    }
    private CreateNovelRequest request(NovelMode mode, Integer deliveryCount, List<Long> invitees) {
        return new CreateNovelRequest(NovelKind.RELAY, "새 이야기", "소개", "판타지", mode != NovelMode.OPEN ? false : true,
                null, 20, 24, null, mode, deliveryCount, invitees, null);
    }
    private void saveReturnsWithId(long id) {
        when(novels.saveAndFlush(any(Novel.class))).thenAnswer(call -> {
            Novel saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", id); ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            return saved;
        });
    }
    @Test @DisplayName("열매로 퍼뜨리는 작품은 정원 없이 바로 연재되고 만든 사람이 1회차를 쓴다")
    void openNovelStartsWithOwnerWriting() {
        saveReturnsWithId(20L);
        NovelDetail created = service.create(1L, request(NovelMode.OPEN, 3, null));
        assertThat(created.novel().mode()).isEqualTo(NovelMode.OPEN);
        assertThat(created.novel().status()).isEqualTo(NovelStatus.ONGOING);
        assertThat(created.novel().memberLimit()).isNull();
        assertThat(created.novel().deliveryCount()).isEqualTo(3);
        assertThat(created.canWrite()).isTrue(); assertThat(created.canApply()).isFalse();
    }
    @Test @DisplayName("열매로 퍼뜨리는 작품은 배달 인원이 없으면 만들 수 없다")
    void openNovelNeedsDeliveryCount() {
        assertThatThrownBy(() -> service.create(1L, request(NovelMode.OPEN, null, null))).isInstanceOf(ApiException.class);
        verify(novels, never()).saveAndFlush(any());
    }
    @Test @DisplayName("열매로 퍼뜨리는 작품의 1회차를 발행하면 배달하고 차례를 비운다")
    void publishingOpenRootDelivers() {
        Novel open = Novel.open(1L, "열매", "소개", "판타지", 20, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 20L); ReflectionTestUtils.setField(open, "createdAt", Instant.now());
        when(novels.lockById(20L)).thenReturn(Optional.of(open));
        when(chapters.saveAndFlush(any(NovelChapter.class))).thenAnswer(call -> call.getArgument(0));
        service.publish(1L, 20L, new NovelWriteRequest(1L, "뿌리", "본문"));
        verify(delivery).deliverNewChapter(eq(open), argThat(c -> c.getChapterNumber() == 1 && c.getParentChapterId() == null), any());
        assertThat(open.getCurrentWriterId()).isNull(); assertThat(open.canWrite(1L)).isFalse();
    }
    @Test @DisplayName("한 줄기 작품의 회차는 마지막 회차를 부모로 둔다")
    void circleChapterHangsOnLastChapter() {
        n.start(Instant.now());
        ReflectionTestUtils.setField(n, "chapterCount", 1);
        NovelChapter last = new NovelChapter(10L, 2L, null, 1, "1화", "본문");
        ReflectionTestUtils.setField(last, "id", 100L);
        when(chapters.findFirstByNovelIdOrderByChapterNumberDescIdDesc(10L)).thenReturn(Optional.of(last));
        when(chapters.saveAndFlush(any(NovelChapter.class))).thenAnswer(call -> call.getArgument(0));
        NovelChapterView published = service.publish(1L, 10L, new NovelWriteRequest(1L, "2화", "본문"));
        assertThat(published.parentChapterId()).isEqualTo(100L); assertThat(published.chapterNumber()).isEqualTo(2);
        verify(delivery, never()).deliverNewChapter(any(), any(), any());
    }
    @Test @DisplayName("모여 만들기는 팔로우하지 않은 사람을 고르면 거절한다")
    void circleRejectsUnfollowedInvitee() {
        List<UserFollow> followed = List.of(follow(1L, 2L));
        when(follows.findAllByFollowerIdAndFolloweeIdIn(1L, List.of(2L, 3L))).thenReturn(followed);
        assertThatThrownBy(() -> service.create(1L, request(NovelMode.CIRCLE, null, List.of(2L, 3L))))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_INVITEE_NOT_FOLLOWED);
        verify(novels, never()).saveAndFlush(any());
    }
    @Test @DisplayName("모여 만들기로 고른 작가는 승인 없이 참가하고 정원은 고른 사람과 나다")
    void circleInviteesJoinWithoutApproval() {
        saveReturnsWithId(30L);
        List<UserFollow> followed = List.of(follow(1L, 2L), follow(1L, 3L));
        when(follows.findAllByFollowerIdAndFolloweeIdIn(1L, List.of(2L, 3L))).thenReturn(followed);
        NovelDetail created = service.create(1L, request(NovelMode.CIRCLE, null, List.of(2L, 3L, 1L, 2L)));
        assertThat(created.novel().memberLimit()).isEqualTo(3);
        assertThat(created.novel().mode()).isEqualTo(NovelMode.CIRCLE);
        verify(members, times(2)).save(argThat(m -> "ACTIVE".equals(m.getStatus()) && m.getNovelId().equals(30L)));
    }
    @Test @DisplayName("열매로 퍼뜨리는 작품에는 참가 신청을 받지 않는다")
    void openNovelRejectsApplications() {
        Novel open = Novel.open(1L, "열매", "소개", "판타지", 20, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 20L);
        when(novels.lockById(20L)).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> service.apply(2L, 20L)).isInstanceOf(ApiException.class);
        verify(members, never()).saveAndFlush(any());
    }
    @Test @DisplayName("완결하면 아직 들고 있는 열매를 거둔다")
    void completingClosesDeliveries() {
        n.start(Instant.now()); ReflectionTestUtils.setField(n, "chapterCount", 2);
        service.complete(1L, 10L);
        verify(delivery).closeAll(10L);
    }
    private static UserFollow follow(Long follower, Long followee) {
        UserFollow f = mock(UserFollow.class);
        when(f.getFollowerId()).thenReturn(follower); when(f.getFolloweeId()).thenReturn(followee);
        return f;
    }
    private static NovelLikeRepository.NovelLikeCount likeCount(Long novelId, long count) {
        return new NovelLikeRepository.NovelLikeCount() {
            public Long getNovelId() { return novelId; }
            public long getLikeCount() { return count; }
        };
    }
}
