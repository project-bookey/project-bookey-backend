package app.bookey.api.club;

import app.bookey.api.club.dto.ClubNoteDtos.*;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.notification.NotificationService.NotificationRequest;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.*;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 모임 노트북 규칙 단위 테스트 — 리포지토리·ClubService 는 Mockito, 저장소·레이트리밋은 기록용 페이크(PostImageServiceTest 선례).
 * 문서는 불투명하므로 서버가 보는 것(요소 수·크기·imageId)만 검증한다.
 */
class ClubNoteServiceTest {

    private static final long ME = 1L, OTHER = 2L, HOST = 3L;
    private static final long CLUB_ID = 10L, OTHER_CLUB = 11L, PAGE_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-09-28T03:00:00Z");
    /** JPEG SOI + APP0 — 스니퍼가 형식만 판별하면 되는 최소 헤더. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};

    /** 저장·삭제 호출을 기록하고 고정 URL 을 돌려주는 페이크. enabled 를 꺼서 STORAGE_DISABLED 경로도 본다. */
    private static final class RecordingStorage implements StorageService {
        boolean enabled = true;
        String key;
        int storeCalls;
        final List<String> deletedKeys = new ArrayList<>();

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public String store(String key, InputStream in, long size, String contentType) {
            storeCalls++;
            this.key = key;
            try {
                in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return "http://localhost:8080/uploads/" + key;
        }

        @Override
        public void delete(String key) {
            deletedKeys.add(key);
        }
    }

    /** Redis 없이 호출 키만 기록하고 항상 허용한다. */
    private static final class RecordingRateLimiter extends RateLimiter {
        final List<String> keys = new ArrayList<>();
        final Map<String, Integer> limits = new HashMap<>();
        final Map<String, Duration> windows = new HashMap<>();

        RecordingRateLimiter() {
            super(null);
        }

        @Override
        public boolean tryAcquire(String key, int limit, Duration window) {
            keys.add(key);
            limits.put(key, limit);
            windows.put(key, window);
            return true;
        }
    }

    private final ClubService clubService = mock(ClubService.class);
    private final ClubRepository clubRepository = mock(ClubRepository.class);
    private final ClubNotePageRepository pageRepository = mock(ClubNotePageRepository.class);
    private final ClubNoteImageRepository imageRepository = mock(ClubNoteImageRepository.class);
    private final ClubMemberRepository memberRepository = mock(ClubMemberRepository.class);
    private final ClubEventRepository eventRepository = mock(ClubEventRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final RecordingStorage storage = new RecordingStorage();
    private final RecordingRateLimiter rateLimiter = new RecordingRateLimiter();
    private final BookeyProperties properties = new BookeyProperties(null, null, null, null, null, null, null, null,
            new BookeyProperties.Storage("local", null, null, null,
                    new BookeyProperties.Storage.Image(10_485_760, 10)), null);
    private final ClubNoteService service = new ClubNoteService(clubService, clubRepository, pageRepository,
            imageRepository, memberRepository, eventRepository, userRepository, notificationService, storage,
            rateLimiter, properties, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final ClubMember me = member(ME, ClubRole.MEMBER);
    private final ClubMember other = member(OTHER, ClubRole.MEMBER);

    // ────────────────────────────── 픽스처 ──────────────────────────────

    private static ClubMember member(long userId, ClubRole role) {
        return ClubMember.builder().clubId(CLUB_ID).userId(userId).readingRecordId(userId * 100)
                .role(role).shareProgress(true).allowNudge(true).build();
    }

    private static Club club() {
        return Club.builder().ownerId(HOST).name("월요일의 데미안").joinCode("ABC234").memberLimit((short) 5)
                .startsAt(LocalDate.of(2026, 9, 1)).endsAt(LocalDate.of(2026, 10, 31)).allowNudge(true).build();
    }

    private static Club endedClub() {
        Club club = club();
        club.end();
        return club;
    }

    private static ClubNotePage page(long id, long clubId, long createdBy, int version) {
        ClubNotePage page = ClubNotePage.create(clubId, 1, "첫 모임", createdBy);
        set(page, "id", id);
        set(page, "version", version);
        return page;
    }

    private static ClubNoteImage image(long id, long clubId, Long pageId) {
        ClubNoteImage image = ClubNoteImage.builder().clubId(clubId).userId(ME).storageKey("k" + id)
                .url("http://x/k" + id).contentType("image/jpeg").byteSize(10).build();
        set(image, "id", id);
        if (pageId != null) {
            image.attach(pageId);
        }
        return image;
    }

    private static User user(long id, String nickname) {
        User user = User.builder().handle("h" + id).email(id + "@dev.local").nickname(nickname)
                .avatarUrl(null).timezone("Asia/Seoul").build();
        set(user, "id", id);
        return user;
    }

    /** id 는 엔티티 자신에, 감사 시각은 상위 클래스에 있어 올라가며 찾는다. */
    private static void set(Object target, String field, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    private static Map<String, Object> doc(List<?> elements) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("v", 1);
        document.put("paper", "plain");
        document.put("elements", elements);
        return document;
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG);
    }

    private void givenMember(ClubMember member, Club club) {
        when(clubService.activeMember(CLUB_ID, member.getUserId())).thenReturn(member);
        when(clubService.getClub(CLUB_ID)).thenReturn(club);
        when(clubRepository.findByIdForUpdate(CLUB_ID)).thenReturn(Optional.of(club));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(ME, "나"), user(OTHER, "너")));
    }

    private void givenSavedPage(ClubNotePage page) {
        when(pageRepository.findById(page.getId())).thenReturn(Optional.of(page));
        when(pageRepository.findByIdForUpdate(page.getId())).thenReturn(Optional.of(page));
        when(pageRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ────────────────────────────── 목록 ──────────────────────────────

    @Test
    @DisplayName("페이지 목록은 문서 없이 요약만 읽고, 편집자 닉네임을 붙인다(탈퇴자는 '알 수 없음')")
    void listsSummariesWithoutDocuments() {
        givenMember(me, club());
        when(pageRepository.findSummaries(CLUB_ID)).thenReturn(List.of(
                new ClubNotePageSummary(100L, 1, "첫 모임", 3, 12, ME, NOW),
                new ClubNotePageSummary(101L, 2, null, 0, 0, 99L, NOW)));

        ClubNotebookView notebook = service.notebook(ME, CLUB_ID);

        assertThat(notebook.readOnly()).isFalse();
        assertThat(notebook.policy().maxPages()).isEqualTo(30);
        assertThat(notebook.policy().maxImageBytes()).isEqualTo(10_485_760);
        assertThat(notebook.pages()).hasSize(2);
        assertThat(notebook.pages().get(0).updatedBy().nickname()).isEqualTo("나");
        assertThat(notebook.pages().get(0).elementCount()).isEqualTo(12);
        assertThat(notebook.pages().get(1).updatedBy().nickname()).isEqualTo("알 수 없음");
        verify(pageRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("끝난 모임은 readOnly 로 내려주되 읽기는 허용한다")
    void endedClubIsReadOnly() {
        givenMember(me, endedClub());
        when(pageRepository.findSummaries(CLUB_ID)).thenReturn(List.of());

        assertThat(service.notebook(ME, CLUB_ID).readOnly()).isTrue();
    }

    // ────────────────────────────── 만들기 ──────────────────────────────

    @Test
    @DisplayName("페이지 생성 — 모임 행을 잠그고 seq 는 마지막+1, 이벤트와 다른 멤버 알림을 남긴다")
    void createsPageAfterLastAndNotifiesOthers() {
        givenMember(me, club());
        when(pageRepository.countByClubId(CLUB_ID)).thenReturn(2L);
        when(pageRepository.findMaxSeq(CLUB_ID)).thenReturn(4);
        when(pageRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ClubNotePage page = inv.getArgument(0);
            set(page, "id", PAGE_ID);
            return page;
        });
        when(memberRepository.findAllByClubIdAndStatus(CLUB_ID, ClubMemberStatus.ACTIVE)).thenReturn(List.of(me, other));

        ClubNotePageView view = service.createPage(ME, CLUB_ID, new CreateClubNotePageRequest("  두 번째 만남 "));

        assertThat(view.seq()).isEqualTo(5);
        assertThat(view.title()).isEqualTo("두 번째 만남");
        assertThat(view.version()).isZero();
        assertThat(view.canDelete()).isTrue();
        assertThat(view.createdBy().nickname()).isEqualTo("나");
        verify(clubRepository).findByIdForUpdate(CLUB_ID);

        ArgumentCaptor<ClubEvent> event = ArgumentCaptor.forClass(ClubEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getType()).isEqualTo(ClubEventType.NOTE_PAGE_ADDED);
        assertThat(event.getValue().getPayload()).containsEntry("pageId", PAGE_ID).containsEntry("seq", 5);

        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).schedule(request.capture());
        assertThat(request.getAllValues()).hasSize(1);
        assertThat(request.getValue().userId()).isEqualTo(OTHER);
        assertThat(request.getValue().type()).isEqualTo(NotificationType.CLUB_NOTE_PAGE);
        assertThat(request.getValue().clubId()).isEqualTo(CLUB_ID);
        assertThat(request.getValue().payload()).containsEntry("pageId", PAGE_ID);
    }

    @Test
    @DisplayName("페이지는 30장까지")
    void rejectsBeyondPageLimit() {
        givenMember(me, club());
        when(pageRepository.countByClubId(CLUB_ID)).thenReturn(30L);

        assertThatThrownBy(() -> service.createPage(ME, CLUB_ID, new CreateClubNotePageRequest(null)))
                .extracting(ClubNoteServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOTE_PAGE_LIMIT);
        verify(pageRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("끝난 모임에는 페이지를 만들·고칠·지울 수 없고 사진도 올릴 수 없다")
    void rejectsWritesOnEndedClub() {
        givenMember(me, endedClub());

        assertThatThrownBy(() -> service.createPage(ME, CLUB_ID, new CreateClubNotePageRequest(null)))
                .extracting(ClubNoteServiceTest::codeOf).isEqualTo(ErrorCode.CLUB_ENDED);
        assertThatThrownBy(() -> service.savePage(ME, CLUB_ID, PAGE_ID,
                new SaveClubNotePageRequest(0, null, doc(List.of()))))
                .extracting(ClubNoteServiceTest::codeOf).isEqualTo(ErrorCode.CLUB_ENDED);
        assertThatThrownBy(() -> service.deletePage(ME, CLUB_ID, PAGE_ID))
                .extracting(ClubNoteServiceTest::codeOf).isEqualTo(ErrorCode.CLUB_ENDED);
        assertThatThrownBy(() -> service.uploadImage(ME, CLUB_ID, jpeg()))
                .extracting(ClubNoteServiceTest::codeOf).isEqualTo(ErrorCode.CLUB_ENDED);
        assertThat(storage.storeCalls).isZero();
    }

    // ────────────────────────────── 저장 ──────────────────────────────

    @Test
    @DisplayName("저장 — 버전이 다르면 CLUB_NOTE_CONFLICT 이고 문서·버전은 그대로다")
    void rejectsStaleVersion() {
        givenMember(me, club());
        ClubNotePage page = page(PAGE_ID, CLUB_ID, OTHER, 3);
        givenSavedPage(page);

        assertThatThrownBy(() -> service.savePage(ME, CLUB_ID, PAGE_ID,
                new SaveClubNotePageRequest(2, "고친 제목", doc(List.of(Map.of("type", "text"))))))
                .extracting(ClubNoteServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOTE_CONFLICT);
        assertThat(page.getVersion()).isEqualTo(3);
        assertThat(page.getTitle()).isEqualTo("첫 모임");
        assertThat(page.getDocument()).isEmpty();
        verify(pageRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("저장 — 버전이 맞으면 덮어쓰고 version+1 · 편집자 · 요소 수를 채운다")
    void overwritesWhenVersionMatches() {
        givenMember(me, club());
        ClubNotePage page = page(PAGE_ID, CLUB_ID, OTHER, 3);
        givenSavedPage(page);
        Map<String, Object> document = doc(List.of(Map.of("type", "text"), Map.of("type", "ink")));

        ClubNotePageSummaryView summary = service.savePage(ME, CLUB_ID, PAGE_ID,
                new SaveClubNotePageRequest(3, " 고친 제목 ", document));

        assertThat(summary.version()).isEqualTo(4);
        assertThat(summary.elementCount()).isEqualTo(2);
        assertThat(summary.title()).isEqualTo("고친 제목");
        assertThat(summary.updatedBy().userId()).isEqualTo(ME);
        assertThat(page.getDocument()).isSameAs(document);
        assertThat(page.getUpdatedBy()).isEqualTo(ME);
        assertThat(rateLimiter.keys).contains("club:note:save:" + ME);
        assertThat(rateLimiter.limits).containsEntry("club:note:save:" + ME, 60);
    }

    @Test
    @DisplayName("저장 — elements 가 300개를 넘으면 잠그기 전에 INVALID_REQUEST")
    void rejectsTooManyElements() {
        givenMember(me, club());
        List<Object> elements = new ArrayList<>();
        for (int i = 0; i < 301; i++) {
            elements.add(Map.of("type", "sticker"));
        }

        assertThatThrownBy(() -> service.savePage(ME, CLUB_ID, PAGE_ID, new SaveClubNotePageRequest(0, null, doc(elements))))
                .extracting(ClubNoteServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(pageRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("저장 — elements 가 배열이 아니면 INVALID_REQUEST")
    void rejectsNonArrayElements() {
        givenMember(me, club());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("elements", "x");

        assertThatThrownBy(() -> service.savePage(ME, CLUB_ID, PAGE_ID, new SaveClubNotePageRequest(0, null, document)))
                .extracting(ClubNoteServiceTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(pageRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("저장 — 직렬화 512KB 를 넘으면 잠그기 전에 CLUB_NOTE_TOO_LARGE")
    void rejectsOversizedDocument() {
        givenMember(me, club());
        Map<String, Object> document = doc(List.of(Map.of("type", "text", "text", "가".repeat(600 * 1024))));

        assertThatThrownBy(() -> service.savePage(ME, CLUB_ID, PAGE_ID, new SaveClubNotePageRequest(0, null, document)))
                .extracting(ClubNoteServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOTE_TOO_LARGE);
        verify(pageRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("저장 — 문서에서 빠진 사진은 떼고 새로 참조한 사진은 붙인다; 다른 모임·다른 페이지·모르는 id 는 무시한다")
    void syncsReferencedImages() {
        givenMember(me, club());
        ClubNotePage page = page(PAGE_ID, CLUB_ID, ME, 0);
        givenSavedPage(page);
        ClubNoteImage kept = image(1L, CLUB_ID, PAGE_ID);
        ClubNoteImage removed = image(2L, CLUB_ID, PAGE_ID);
        ClubNoteImage fresh = image(3L, CLUB_ID, null);
        ClubNoteImage foreign = image(4L, OTHER_CLUB, null);
        ClubNoteImage elsewhere = image(5L, CLUB_ID, 200L);
        when(imageRepository.findAllByPageId(PAGE_ID)).thenReturn(List.of(kept, removed));
        when(imageRepository.findAllById(any())).thenReturn(List.of(fresh, foreign, elsewhere));
        Map<String, Object> document = doc(List.of(
                Map.of("type", "photo", "imageId", 1),
                Map.of("type", "photo", "imageId", 3),
                Map.of("type", "photo", "imageId", 4),
                Map.of("type", "photo", "imageId", 5),
                Map.of("type", "photo", "imageId", 6),
                Map.of("type", "ink")));

        service.savePage(ME, CLUB_ID, PAGE_ID, new SaveClubNotePageRequest(0, null, document));

        assertThat(kept.getPageId()).isEqualTo(PAGE_ID);
        assertThat(removed.isDetached()).isTrue();
        assertThat(fresh.getPageId()).isEqualTo(PAGE_ID);
        assertThat(foreign.isDetached()).isTrue();
        assertThat(elsewhere.getPageId()).isEqualTo(200L);
        assertThat(storage.deletedKeys).isEmpty();
    }

    @Test
    @DisplayName("다른 모임의 페이지 id 는 CLUB_NOTE_PAGE_NOT_FOUND")
    void rejectsPageOfOtherClub() {
        givenMember(me, club());
        givenSavedPage(page(PAGE_ID, OTHER_CLUB, ME, 0));

        assertThatThrownBy(() -> service.page(ME, CLUB_ID, PAGE_ID))
                .extracting(ClubNoteServiceTest::codeOf).isEqualTo(ErrorCode.CLUB_NOTE_PAGE_NOT_FOUND);
        assertThatThrownBy(() -> service.savePage(ME, CLUB_ID, PAGE_ID, new SaveClubNotePageRequest(0, null, doc(List.of()))))
                .extracting(ClubNoteServiceTest::codeOf).isEqualTo(ErrorCode.CLUB_NOTE_PAGE_NOT_FOUND);
    }

    // ────────────────────────────── 삭제 ──────────────────────────────

    @Test
    @DisplayName("삭제 — 만든 사람이 아니고 운영자도 아니면 FORBIDDEN")
    void deleteRequiresCreatorOrModerator() {
        givenMember(me, club());
        givenSavedPage(page(PAGE_ID, CLUB_ID, OTHER, 0));

        assertThatThrownBy(() -> service.deletePage(ME, CLUB_ID, PAGE_ID))
                .extracting(ClubNoteServiceTest::codeOf)
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(pageRepository, never()).delete(any(ClubNotePage.class));
    }

    @Test
    @DisplayName("삭제 — 호스트는 남의 페이지도 지우고, 사진은 떼기만 하고 파일은 지우지 않는다")
    void hostDeletesAndOnlyDetachesImages() {
        ClubMember host = member(HOST, ClubRole.HOST);
        givenMember(host, club());
        ClubNotePage page = page(PAGE_ID, CLUB_ID, OTHER, 0);
        givenSavedPage(page);

        service.deletePage(HOST, CLUB_ID, PAGE_ID);

        verify(imageRepository).detachAllByPageId(PAGE_ID);
        verify(pageRepository).delete(page);
        assertThat(storage.deletedKeys).isEmpty();
    }

    // ────────────────────────────── 사진 ──────────────────────────────

    @Test
    @DisplayName("사진 업로드 — 키는 clubs/{clubId}/notebook/{userId}/yyyy/MM/{uuid}.jpg, page_id 없이 행을 저장한다")
    void uploadsImageUnderNotebookKey() {
        givenMember(me, club());
        when(imageRepository.save(any())).thenAnswer(inv -> {
            ClubNoteImage image = inv.getArgument(0);
            set(image, "id", 7L);
            return image;
        });

        ClubNoteImageView view = service.uploadImage(ME, CLUB_ID, jpeg());

        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.url()).isEqualTo("http://localhost:8080/uploads/" + storage.key);
        assertThat(storage.key).matches("clubs/10/notebook/1/2026/09/[0-9a-f-]{36}\\.jpg");
        ArgumentCaptor<ClubNoteImage> saved = ArgumentCaptor.forClass(ClubNoteImage.class);
        verify(imageRepository).save(saved.capture());
        assertThat(saved.getValue().isDetached()).isTrue();
        assertThat(saved.getValue().getClubId()).isEqualTo(CLUB_ID);
        assertThat(saved.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(rateLimiter.keys).contains("club:note:image:" + ME);
        assertThat(rateLimiter.limits).containsEntry("club:note:image:" + ME, 30);
        assertThat(rateLimiter.windows).containsEntry("club:note:image:" + ME, Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("사진 업로드 — 저장소가 꺼져 있으면 파일을 읽기 전에 STORAGE_DISABLED")
    void rejectsUploadWhenStorageDisabled() {
        givenMember(me, club());
        storage.enabled = false;

        assertThatThrownBy(() -> service.uploadImage(ME, CLUB_ID, jpeg()))
                .extracting(ClubNoteServiceTest::codeOf)
                .isEqualTo(ErrorCode.STORAGE_DISABLED);
        assertThat(storage.storeCalls).isZero();
        assertThat(rateLimiter.keys).isEmpty();
    }

    @Test
    @DisplayName("사진 업로드 — 행 저장이 실패하면 올린 파일을 지운다(보상)")
    void deletesFileWhenRowSaveFails() {
        givenMember(me, club());
        when(imageRepository.save(any())).thenThrow(new RuntimeException("DB 끊김"));

        assertThatThrownBy(() -> service.uploadImage(ME, CLUB_ID, jpeg())).isInstanceOf(RuntimeException.class);

        assertThat(storage.storeCalls).isEqualTo(1);
        assertThat(storage.deletedKeys).containsExactly(storage.key);
    }

    @Test
    @DisplayName("사진 업로드는 DB 트랜잭션 밖에서 파일을 쓴다 — 메서드·클래스 어디에도 @Transactional 이 없다")
    void uploadRunsOutsideDbTransaction() throws NoSuchMethodException {
        assertThat(ClubNoteService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(ClubNoteService.class.getMethod("uploadImage", Long.class, Long.class, MultipartFile.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    // ────────────────────────────── 순수 함수 ──────────────────────────────

    @Test
    @DisplayName("referencedImageIds — 숫자 imageId 만 모으고, 맵이 아니거나 id 가 없는 요소는 건너뛴다")
    void collectsNumericImageIdsOnly() {
        List<Object> elements = new ArrayList<>();
        elements.add(Map.of("type", "photo", "imageId", 1));
        elements.add(Map.of("type", "photo", "imageId", 2.0));
        elements.add(Map.of("type", "photo", "imageId", "x"));
        elements.add("문자열");
        elements.add(Map.of("type", "ink"));

        assertThat(ClubNoteService.referencedImageIds(doc(elements))).containsExactlyInAnyOrder(1L, 2L);
        assertThat(ClubNoteService.referencedImageIds(Map.of())).isEmpty();
        assertThat(ClubNoteService.elementCount(Map.of())).isZero();
        assertThat(ClubNoteService.elementCount(doc(elements))).isEqualTo(5);
    }
}
