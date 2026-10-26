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
            mock(RateLimiter.class), deliveries, delivery, baseWorks, follows, mock(NovelSunlightService.class));
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
    @Test @DisplayName("열매 탭 목록은 고른 장르와 검색어로 거르고, %·_ 는 글자 그대로 찾는다")
    void feedSearchesByGenresAndKeyword() {
        var page = PageRequest.of(0, 20);
        when(novels.search(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(n), page, 1));

        service.feed(2L, NovelKind.RELAY, NovelStatus.ONGOING, List.of(" 무협 ", "스릴러", "무협"), "  100%_달 ", page);

        verify(novels).search("RELAY", "ONGOING", "무협,스릴러", "%100!%!_달%", page);
        verify(novels, never()).feed(any(), any(), any());
    }
    @Test @DisplayName("열매 탭 목록은 장르만 고르면 검색어 없이, 아무것도 없으면 예전 피드로 읽는다")
    void feedWithoutFiltersUsesPlainFeed() {
        var page = PageRequest.of(0, 20);
        when(novels.search(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(), page, 0));
        when(novels.feed(NovelKind.RELAY, null, page)).thenReturn(new PageImpl<>(List.of(), page, 0));

        service.feed(2L, NovelKind.RELAY, null, List.of("판타지"), " ", page);
        service.feed(2L, NovelKind.RELAY, null, null, null, page);

        verify(novels).search("RELAY", "", "판타지", "", page);
        verify(novels).feed(NovelKind.RELAY, null, page);
    }
    @Test @DisplayName("열매 탭 목록은 장르를 넷 이상 고르거나 목록에 없는 장르를 받지 않는다")
    void feedRejectsTooManyOrUnknownGenres() {
        var page = PageRequest.of(0, 20);

        assertThatThrownBy(() -> service.feed(2L, NovelKind.RELAY, null, List.of("판타지", "무협", "공포", "SF"), null, page))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.feed(2L, NovelKind.RELAY, null, List.of("없는장르"), null, page))
                .isInstanceOf(ApiException.class);
        verify(novels, never()).search(any(), any(), any(), any(), any());
    }
    @Test @DisplayName("순위는 지난 7일 동안 많이 이어 쓴 작품 열 편을 고른 장르로 거른다")
    void weeklyRankingReadsLastSevenDaysByGenres() {
        when(novels.mostContinuedSince(any(), any(), any(), anyInt())).thenReturn(List.of(n));
        Instant before = Instant.now();

        var ranked = service.weeklyRanking(2L, NovelKind.RELAY, List.of(" 무협 ", "판타지"));

        var since = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(novels).mostContinuedSince(eq("RELAY"), eq("무협,판타지"), since.capture(), eq(10));
        assertThat(since.getValue()).isBetween(before.minus(java.time.Duration.ofDays(7)), Instant.now().minus(java.time.Duration.ofDays(7)));
        assertThat(ranked).extracting(NovelSummary::id).containsExactly(10L);
    }
    @Test @DisplayName("순위는 장르를 안 고르면 모든 장르에서, 넷 이상이거나 목록에 없는 장르는 받지 않는다")
    void weeklyRankingGenres() {
        when(novels.mostContinuedSince(any(), any(), any(), anyInt())).thenReturn(List.of());

        service.weeklyRanking(2L, NovelKind.RELAY, null);

        verify(novels).mostContinuedSince(eq("RELAY"), eq(""), any(), eq(10));
        assertThatThrownBy(() -> service.weeklyRanking(2L, NovelKind.RELAY, List.of("판타지", "무협", "공포", "SF")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.weeklyRanking(2L, NovelKind.RELAY, List.of("없는장르")))
                .isInstanceOf(ApiException.class);
    }
    @Test @DisplayName("내가 이어 쓴 이야기는 내 작품 목록과 따로, 한 페이지의 좋아요를 한 번에 실어 읽는다")
    void continuedReadsNovelsIBranchedInto() {
        var page = PageRequest.of(0, 20);
        when(novels.continuedBy(2L, "RELAY", page)).thenReturn(new PageImpl<>(List.of(n), page, 1));

        var found = service.continued(2L, NovelKind.RELAY, page).content();

        assertThat(found).extracting(NovelSummary::id).containsExactly(10L);
        verify(novels, never()).mine(any(), any(), any());
        verify(likes, times(1)).countPerNovel(any());
    }
    private CreateNovelRequest request(NovelMode mode, Integer branchLimit, List<Long> invitees) {
        return request(mode, branchLimit, invitees, "판타지", null);
    }
    private CreateNovelRequest request(NovelMode mode, Integer branchLimit, List<Long> invitees, String genre, List<String> genres) {
        return new CreateNovelRequest(NovelKind.RELAY, "새 이야기", "소개", genre, mode != NovelMode.OPEN ? false : true,
                null, 20, 24, null, mode, null, invitees, null, branchLimit, new FirstChapterRequest("1화", "첫 이야기"), genres);
    }
    @Test @DisplayName("장르는 세 개까지 고른 순서대로 두고, 첫 번째가 대표 장르다")
    void createKeepsPickedGenresInOrder() {
        saveReturnsWithId(22L);
        NovelDetail created = service.create(1L, request(NovelMode.OPEN, null, null, null, List.of("미스터리", "판타지", "미스터리", "공포")));
        assertThat(created.novel().genres()).containsExactly("미스터리", "판타지", "공포");
        assertThat(created.novel().genre()).isEqualTo("미스터리");
    }
    @Test @DisplayName("장르를 비우면 예전 앱처럼 한 장르로 만든다")
    void createFallsBackToSingleGenre() {
        saveReturnsWithId(23L);
        NovelDetail created = service.create(1L, request(NovelMode.OPEN, null, null, "일상", List.of()));
        assertThat(created.novel().genres()).containsExactly("일상");
    }
    @Test @DisplayName("장르가 없거나 넷 이상이거나 목록에 없으면 만들지 않는다")
    void createRejectsInvalidGenres() {
        assertThatThrownBy(() -> service.create(1L, request(NovelMode.OPEN, null, null, null, null)))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.create(1L, request(NovelMode.OPEN, null, null, null, List.of("판타지", "일상", "SF", "공포"))))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.create(1L, request(NovelMode.OPEN, null, null, null, List.of("요리"))))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(novels, never()).saveAndFlush(any());
    }
    private void saveReturnsWithId(long id) {
        when(novels.saveAndFlush(any(Novel.class))).thenAnswer(call -> {
            Novel saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", id); ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            return saved;
        });
        when(chapters.saveAndFlush(any(NovelChapter.class))).thenAnswer(call -> call.getArgument(0));
    }
    @Test @DisplayName("열매로 퍼뜨리는 작품은 정원 없이 바로 연재되고, 만든 사람의 1회차가 줄기의 뿌리가 되며 아무에게도 배달하지 않는다")
    void openNovelStartsWithOwnersFirstChapter() {
        saveReturnsWithId(20L);
        NovelDetail created = service.create(1L, request(NovelMode.OPEN, 4, null));
        assertThat(created.novel().mode()).isEqualTo(NovelMode.OPEN);
        assertThat(created.novel().status()).isEqualTo(NovelStatus.ONGOING);
        assertThat(created.novel().memberLimit()).isNull();
        assertThat(created.novel().deliveryCount()).isNull();
        assertThat(created.novel().branchLimit()).isEqualTo(4);
        assertThat(created.novel().chapterCount()).isEqualTo(1);
        // 만든 사람은 그 뒤로도 줄기를 이어 쓸 수 있다.
        assertThat(created.canWrite()).isTrue(); assertThat(created.canApply()).isFalse();
        verify(chapters).saveAndFlush(argThat(c -> c.isTrunk() && c.getChapterNumber() == 1
                && c.getParentChapterId() == null && c.getAuthorId().equals(1L) && "첫 이야기".equals(c.getBody())));
        verify(deliveries, never()).saveAll(any());
    }
    @Test @DisplayName("1회차 없이 작품을 만들면 거절한다")
    void createRequiresFirstChapter() {
        var violations = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new CreateNovelRequest(NovelKind.RELAY, "새 이야기", "소개", "판타지", true, null, 20, 24, null,
                        NovelMode.OPEN, null, null, null, null, null, List.of("판타지")));
        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("firstChapter");
    }
    @Test @DisplayName("가지 상한을 비우면 3으로 만든다")
    void openNovelDefaultsBranchLimit() {
        saveReturnsWithId(21L);
        NovelDetail created = service.create(1L, request(NovelMode.OPEN, null, null));
        assertThat(created.novel().branchLimit()).isEqualTo(Novel.BRANCH_LIMIT_DEFAULT).isEqualTo(3);
    }
    @Test @DisplayName("가지 상한은 2~5개만 받는다")
    void branchLimitMustBeBetweenTwoAndFive() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(request(NovelMode.OPEN, 1, null))).isNotEmpty();
        assertThat(validator.validate(request(NovelMode.OPEN, 6, null))).isNotEmpty();
        assertThat(validator.validate(request(NovelMode.OPEN, 2, null))).isEmpty();
        assertThat(validator.validate(request(NovelMode.OPEN, 5, null))).isEmpty();
    }
    @Test @DisplayName("회차를 열면 글쓴이가 아닌 사람만 한 번 조회수에 센다")
    void chapterNodeCountsViewsOncePerReader() {
        Novel open = Novel.open(1L, "열매", "소개", "판타지", 20, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 20L);
        when(novels.findById(20L)).thenReturn(Optional.of(open));
        NovelChapter c = new NovelChapter(20L, 3L, 100L, 2, "가지", "본문");
        ReflectionTestUtils.setField(c, "id", 200L); ReflectionTestUtils.setField(c, "viewCount", 4);
        when(chapters.findByIdAndNovelId(200L, 20L)).thenReturn(Optional.of(c));
        when(chapters.recordView(200L, 5L)).thenReturn(1, 0);

        assertThat(service.chapterNode(5L, 20L, 200L).viewCount()).isEqualTo(5);
        assertThat(service.chapterNode(5L, 20L, 200L).viewCount()).isEqualTo(4);
        service.chapterNode(3L, 20L, 200L);

        verify(chapters, times(1)).incrementViews(200L);
        verify(chapters, never()).recordView(200L, 3L);
    }
    @Test @DisplayName("수거함에 들어간 가지는 나무에서 숨겨 읽을 수 없다")
    void prunedChapterIsHidden() {
        Novel open = Novel.open(1L, "열매", "소개", "판타지", 20, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 20L);
        when(novels.findById(20L)).thenReturn(Optional.of(open));
        NovelChapter c = new NovelChapter(20L, 3L, 100L, 2, "가지", "본문");
        c.prune(Instant.now());
        when(chapters.findByIdAndNovelId(200L, 20L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.chapterNode(5L, 20L, 200L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verify(chapters, never()).recordView(any(), any());
    }
    /** 1회차까지 올린 열매로 퍼뜨리는 작품(목표 회차 3)과 그 뿌리 회차. */
    private Novel openWithRoot(NovelChapter root) {
        Novel open = Novel.open(1L, "열매", "소개", "판타지", 3, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 20L); ReflectionTestUtils.setField(open, "createdAt", Instant.now());
        ReflectionTestUtils.setField(open, "chapterCount", 1); ReflectionTestUtils.setField(open, "turnNumber", 2L);
        ReflectionTestUtils.setField(open, "currentWriterId", null);
        ReflectionTestUtils.setField(root, "id", 100L);
        when(novels.lockById(20L)).thenReturn(Optional.of(open)); when(novels.findById(20L)).thenReturn(Optional.of(open));
        when(chapters.findFirstByNovelIdAndTrunkTrueOrderByChapterNumberDesc(20L)).thenReturn(Optional.of(root));
        when(chapters.saveAndFlush(any(NovelChapter.class))).thenAnswer(call -> {
            NovelChapter saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });
        return open;
    }
    @Test @DisplayName("첫 작가는 마감 없이 줄기 끝에서 위로 이어 쓰고, 아무에게도 배달하지 않는다")
    void ownerGrowsTheTrunkUpward() {
        Novel open = openWithRoot(NovelChapter.trunk(20L, 1L, null, "뿌리", "본문"));
        assertThat(service.get(1L, 20L).canWrite()).isTrue();
        assertThat(service.draft(1L, 20L).turnNumber()).isEqualTo(2L);

        NovelChapterView second = service.publish(1L, 20L, new NovelWriteRequest(2L, "2장", "줄기를 잇는다"));

        assertThat(second.parentChapterId()).isEqualTo(100L);
        assertThat(second.chapterNumber()).isEqualTo(2);
        assertThat(second.trunk()).isTrue();
        assertThat(open.getChapterCount()).isEqualTo(2);
        assertThat(open.getTurnNumber()).isEqualTo(3L);
        assertThat(open.getCurrentWriterId()).isNull();
        verify(deliveries, never()).saveAll(any());
    }
    @Test @DisplayName("줄기는 첫 작가만 쓰고, 낡은 차례 번호로는 쓰지 못한다")
    void onlyOwnerWritesTheTrunk() {
        openWithRoot(NovelChapter.trunk(20L, 1L, null, "뿌리", "본문"));
        assertThat(service.get(2L, 20L).canWrite()).isFalse();
        assertThatThrownBy(() -> service.publish(2L, 20L, new NovelWriteRequest(2L, "남의 줄기", "본문")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_NOT_YOUR_TURN);
        assertThatThrownBy(() -> service.publish(1L, 20L, new NovelWriteRequest(1L, "낡은 쓰기", "본문")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_NOT_YOUR_TURN);
        verify(chapters, never()).saveAndFlush(any());
    }
    @Test @DisplayName("줄기가 목표 회차에 닿으면 더 잇지 않는다")
    void trunkStopsAtTheTargetDepth() {
        NovelChapter tip = new NovelChapter(20L, 1L, 99L, 3, "3장", "본문");
        ReflectionTestUtils.setField(tip, "trunk", true);
        openWithRoot(tip);
        assertThat(service.get(1L, 20L).canWrite()).isFalse();
        assertThatThrownBy(() -> service.publish(1L, 20L, new NovelWriteRequest(2L, "4장", "본문")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOVEL_NOT_YOUR_TURN);
    }
    @Test @DisplayName("회차 나무의 마디는 첫 작가의 줄기인지 알려 준다")
    void chapterTreeMarksTrunk() {
        NovelChapter root = NovelChapter.trunk(20L, 1L, null, "뿌리", "본문");
        openWithRoot(root);
        NovelChapter branch = new NovelChapter(20L, 3L, 100L, 2, "가지", "본문");
        ReflectionTestUtils.setField(branch, "id", 200L);
        when(chapters.findByNovelIdAndStateOrderByIdAsc(20L, NovelChapterState.ACTIVE)).thenReturn(List.of(root, branch));
        assertThat(service.chapterTree(5L, 20L)).extracting(NovelChapterNode::trunk).containsExactly(true, false);
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
        assertThat(published.trunk()).isFalse();
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
    @Test @DisplayName("모여 만들기는 만든 사람의 1회차를 올리며 바로 시작하고 다음 작가에게 차례를 넘긴다")
    void circleStartsWithOwnersFirstChapter() {
        saveReturnsWithId(30L);
        List<UserFollow> followed = List.of(follow(1L, 2L));
        when(follows.findAllByFollowerIdAndFolloweeIdIn(1L, List.of(2L))).thenReturn(followed);
        when(members.activeMembers(30L)).thenReturn(List.of(new NovelMember(30L, 1L, true), new NovelMember(30L, 2L, true)));
        NovelDetail created = service.create(1L, request(NovelMode.CIRCLE, null, List.of(2L)));
        assertThat(created.novel().status()).isEqualTo(NovelStatus.ONGOING);
        assertThat(created.novel().chapterCount()).isEqualTo(1);
        assertThat(created.canWrite()).isFalse(); assertThat(created.currentWriterNickname()).isNotNull();
        verify(chapters).saveAndFlush(argThat(c -> c.getChapterNumber() == 1 && c.getAuthorId().equals(1L) && !c.isTrunk()));
    }
    @Test @DisplayName("모여 만들기는 함께 쓸 작가를 고르지 않으면 만들지 않는다")
    void circleRequiresInvitee() {
        assertThatThrownBy(() -> service.create(1L, request(NovelMode.CIRCLE, null, List.of())))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(novels, never()).saveAndFlush(any());
    }
    @Test @DisplayName("열매로 퍼뜨리는 작품에는 참가 신청을 받지 않는다")
    void openNovelRejectsApplications() {
        Novel open = Novel.open(1L, "열매", "소개", "판타지", 20, 24, 3, null);
        ReflectionTestUtils.setField(open, "id", 20L);
        when(novels.lockById(20L)).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> service.apply(2L, 20L)).isInstanceOf(ApiException.class);
        verify(members, never()).saveAndFlush(any());
    }
    @Test @DisplayName("완결하면 아직 맡은 자리를 거둔다")
    void completingClosesDeliveries() {
        n.start(Instant.now()); ReflectionTestUtils.setField(n, "chapterCount", 2);
        service.complete(1L, 10L);
        verify(delivery).closeAll(n);
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
