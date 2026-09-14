package app.bookey.api.club;

import app.bookey.api.club.ClubLogService.CreateLogCommand;
import app.bookey.api.club.dto.ClubDtos.ClubLogDayCount;
import app.bookey.api.club.dto.ClubDtos.ClubPostView;
import app.bookey.api.club.dto.ClubDtos.ReadingNowView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.*;
import app.bookey.domain.reading.ReadingSession;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 읽기로그 — 조각 검증, 요일 스트립 채우기, 지금 읽는 중 필터. */
class ClubLogServiceTest {

    private static final long ME = 1L, OTHER = 2L, PRIVATE = 3L;
    private static final long CLUB_ID = 10L, MY_RECORD = 100L, OTHER_RECORD = 200L, PRIVATE_RECORD = 300L;
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z"); // KST 21:00
    /** JPEG SOI + APP0 — 스니퍼가 형식만 판별하면 되는 최소 헤더. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};

    private final ClubService clubService = mock(ClubService.class);
    private final ClubPostService postService = mock(ClubPostService.class);
    private final ClubPostRepository postRepository = mock(ClubPostRepository.class);
    private final ClubMemberRepository memberRepository = mock(ClubMemberRepository.class);
    private final ClubBookRepository clubBookRepository = mock(ClubBookRepository.class);
    private final ReadingSessionRepository sessionRepository = mock(ReadingSessionRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final BookeyProperties properties = new BookeyProperties(null, null, null, null, null, null, null, null,
            new BookeyProperties.Storage("local", null, null, new BookeyProperties.Storage.Image(10_485_760, 10)), null);
    private final ClubLogService service = new ClubLogService(clubService, postService, postRepository,
            memberRepository, clubBookRepository, sessionRepository, mock(UserRepository.class), storage,
            mock(RateLimiter.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private final ClubMember me = member(ME, MY_RECORD, true);

    private static ClubMember member(long userId, long recordId, boolean shareProgress) {
        return ClubMember.builder().clubId(CLUB_ID).userId(userId).readingRecordId(recordId)
                .role(ClubRole.MEMBER).shareProgress(shareProgress).allowNudge(true).build();
    }

    private Club club() {
        return Club.builder().ownerId(ME).name("월요일의 데미안").joinCode("ABC234").memberLimit((short) 3)
                .startsAt(LocalDate.of(2026, 9, 1)).endsAt(LocalDate.of(2026, 9, 30)).allowNudge(true).build();
    }

    private void givenActiveClub(Club club) {
        when(clubService.activeMember(CLUB_ID, ME)).thenReturn(me);
        when(clubService.getClub(CLUB_ID)).thenReturn(club);
        when(clubBookRepository.findFirstByClubIdOrderBySeqAsc(CLUB_ID)).thenReturn(Optional.empty());
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postService.viewsFor(eq(me), any())).thenReturn(List.of(mock(ClubPostView.class)));
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @Test
    @DisplayName("사진도 한 줄도 없으면 남길 수 없다")
    void rejectsEmptyLog() {
        givenActiveClub(club());

        assertThatThrownBy(() -> service.create(ME, CLUB_ID, new CreateLogCommand("  ", null, null, null), null))
                .extracting(ClubLogServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("한 줄은 100자까지")
    void rejectsLongBody() {
        givenActiveClub(club());

        assertThatThrownBy(() -> service.create(ME, CLUB_ID,
                new CreateLogCommand("가".repeat(101), null, null, null), null))
                .extracting(ClubLogServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("끝난 모임에는 조각을 남길 수 없다")
    void rejectsEndedClub() {
        Club club = club();
        club.end();
        givenActiveClub(club);

        assertThatThrownBy(() -> service.create(ME, CLUB_ID, new CreateLogCommand("한 줄", null, null, null), null))
                .extracting(ClubLogServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_ENDED);
    }

    @Test
    @DisplayName("다른 책(또는 남)의 세션은 붙일 수 없다")
    void rejectsForeignSession() {
        givenActiveClub(club());
        ReadingSession otherBook = ReadingSession.builder().readingRecordId(999L).userId(ME).startedAt(NOW).build();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(otherBook));

        assertThatThrownBy(() -> service.create(ME, CLUB_ID, new CreateLogCommand("한 줄", 87, null, 7L), null))
                .extracting(ClubLogServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("사진 조각은 모임 경로 키로 저장하고, 쪽에 붙여 LOG 로 남긴다")
    void storesPhotoLog() throws Exception {
        givenActiveClub(club());
        ReadingSession mine = ReadingSession.builder().readingRecordId(MY_RECORD).userId(ME).startedAt(NOW).build();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(mine));
        when(storage.enabled()).thenReturn(true);
        when(storage.store(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));

        service.create(ME, CLUB_ID, new CreateLogCommand(" 창가 자리 ", 87, null, 7L),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG));

        ArgumentCaptor<ClubPost> saved = ArgumentCaptor.forClass(ClubPost.class);
        verify(postRepository).save(saved.capture());
        ClubPost log = saved.getValue();
        assertThat(log.getType()).isEqualTo(ClubPostType.LOG);
        assertThat(log.getBody()).isEqualTo("창가 자리");
        assertThat(log.getAnchorPage()).isEqualTo(87);
        assertThat(log.getSpoilerLevel()).isEqualTo(SpoilerLevel.PAGE);
        assertThat(log.getReadingSessionId()).isEqualTo(7L);
        assertThat(log.getImageStorageKey()).startsWith("clubs/10/1/2026/09/").endsWith(".jpg");
        assertThat(log.getImageUrl()).isEqualTo("https://cdn/" + log.getImageStorageKey());
    }

    @Test
    @DisplayName("저장이 실패하면 올린 사진을 지운다")
    void deletesPhotoWhenSaveFails() throws Exception {
        givenActiveClub(club());
        when(storage.enabled()).thenReturn(true);
        when(storage.store(anyString(), any(InputStream.class), anyLong(), anyString())).thenReturn("https://cdn/x");
        when(postRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.create(ME, CLUB_ID, new CreateLogCommand(null, null, null, null),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG)))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete(org.mockito.ArgumentMatchers.startsWith("clubs/10/1/"));
    }

    @Test
    @DisplayName("요일 스트립은 KST 날짜로 묶고 조각 없는 날은 0 으로 채운다")
    void daysGroupByKstAndFillZero() {
        when(clubService.activeMember(CLUB_ID, ME)).thenReturn(me);
        when(postRepository.findLogTimes(eq(CLUB_ID), any(), any())).thenReturn(List.of(
                Instant.parse("2026-09-07T15:30:00Z"),   // KST 9/8 00:30
                Instant.parse("2026-09-08T14:59:00Z"),   // KST 9/8 23:59
                Instant.parse("2026-09-10T01:00:00Z"))); // KST 9/10

        List<ClubLogDayCount> days = service.days(ME, CLUB_ID, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10));

        assertThat(days).extracting(ClubLogDayCount::logCount).containsExactly(2, 0, 1);
    }

    @Test
    @DisplayName("요일 스트립은 14일까지만 받는다")
    void daysRejectsLongRange() {
        when(clubService.activeMember(CLUB_ID, ME)).thenReturn(me);

        assertThatThrownBy(() -> service.days(ME, CLUB_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)))
                .extracting(ClubLogServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("지금 읽는 중 — 나·진척 비공개 멤버·4시간 넘은 세션은 뺀다")
    void readingNowFilters() {
        when(clubService.activeMember(CLUB_ID, ME)).thenReturn(me);
        when(memberRepository.findAllByClubIdAndStatus(CLUB_ID, ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                me, member(OTHER, OTHER_RECORD, true), member(PRIVATE, PRIVATE_RECORD, false)));
        ReadingSession fresh = ReadingSession.builder().readingRecordId(OTHER_RECORD).userId(OTHER)
                .startedAt(NOW.minus(Duration.ofMinutes(38))).build();
        ReadingSession stale = ReadingSession.builder().readingRecordId(OTHER_RECORD).userId(OTHER)
                .startedAt(NOW.minus(Duration.ofHours(5))).build();
        when(sessionRepository.findAllByReadingRecordIdInAndEndedAtIsNull(List.of(OTHER_RECORD)))
                .thenReturn(List.of(fresh, stale));

        List<ReadingNowView> now = service.readingNow(ME, CLUB_ID);

        assertThat(now).extracting(ReadingNowView::userId).containsExactly(OTHER);
        assertThat(now.get(0).startedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(38)));
    }
}
