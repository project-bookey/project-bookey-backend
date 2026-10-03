package app.bookey.api.club;

import app.bookey.api.club.ClubMeetingNoteService.MeetingNoteChanged;
import app.bookey.api.club.dto.ClubMeetingNoteDtos.MeetingNoteOpsResult;
import app.bookey.api.club.dto.ClubMeetingNoteDtos.MeetingNoteView;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.*;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 모임 공유 노트 — 권한(활성 멤버)·읽기 전용(끝난 클럽·취소된 모임)·연산 적용과 방송. */
class ClubMeetingNoteServiceTest {

    private static final long CLUB_ID = 10L, OTHER_CLUB = 11L, MEETING_ID = 50L, NOTE_ID = 500L;
    private static final long ME = 1L, OUTSIDER = 9L;
    private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");

    private final ClubService clubService = mock(ClubService.class);
    private final ClubMeetingRepository meetingRepository = mock(ClubMeetingRepository.class);
    private final ClubMeetingNoteRepository noteRepository = mock(ClubMeetingNoteRepository.class);
    private final ClubMeetingNoteImageRepository imageRepository = mock(ClubMeetingNoteImageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BookeyProperties properties = new BookeyProperties(null, null, null, null, null, null, null, null,
            null, null);
    private ClubMeetingNoteService service;

    @BeforeEach
    void setUp() {
        service = new ClubMeetingNoteService(clubService, meetingRepository, noteRepository, imageRepository,
                userRepository, mock(StorageService.class), rateLimiter, properties, new ObjectMapper(), events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var activeClub = club(ClubStatus.ACTIVE);
        when(clubService.getClub(CLUB_ID)).thenReturn(activeClub);
        when(clubService.activeMember(CLUB_ID, OUTSIDER)).thenThrow(ApiException.of(ErrorCode.CLUB_NOT_MEMBER));
        var openMeeting = meeting(CLUB_ID, true);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(openMeeting));
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(noteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ────────────────────────────── 픽스처 ──────────────────────────────

    private static Club club(ClubStatus status) {
        Club club = mock(Club.class);
        when(club.getStatus()).thenReturn(status);
        return club;
    }

    private static ClubMeeting meeting(long clubId, boolean open) {
        ClubMeeting meeting = mock(ClubMeeting.class);
        when(meeting.getId()).thenReturn(MEETING_ID);
        when(meeting.getClubId()).thenReturn(clubId);
        when(meeting.isOpen()).thenReturn(open);
        when(meeting.getTitle()).thenReturn("10월 모임");
        return meeting;
    }

    private static ClubMeetingNote note(Map<String, Object> document, int version) {
        try {
            var ctor = ClubMeetingNote.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ClubMeetingNote note = ctor.newInstance();
            set(note, "id", NOTE_ID);
            set(note, "clubId", CLUB_ID);
            set(note, "meetingId", MEETING_ID);
            set(note, "document", document);
            set(note, "version", version);
            return note;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        Field f = ClubMeetingNote.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static List<Map<String, Object>> ops(String... ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String id : ids) {
            out.add(Map.of("t", "upsert", "el", Map.of("id", id, "type", "text")));
        }
        return out;
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(code);
    }

    // ────────────────────────────── 조회 ──────────────────────────────

    @Test
    @DisplayName("아직 아무도 쓰지 않은 노트는 행을 만들지 않고 빈 대형노트를 돌려준다")
    void getEmptyNote() {
        when(noteRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.empty());

        MeetingNoteView view = service.get(ME, CLUB_ID, MEETING_ID);

        assertThat(view.id()).isNull();
        assertThat(view.version()).isZero();
        assertThat(view.document()).containsEntry("kind", "large");
        assertThat(view.readOnly()).isFalse();
        verify(noteRepository, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("클럽 멤버가 아니면 노트를 볼 수 없다 — CLUB_NOT_MEMBER")
    void getRejectsOutsider() {
        assertCode(() -> service.get(OUTSIDER, CLUB_ID, MEETING_ID), ErrorCode.CLUB_NOT_MEMBER);
    }

    @Test
    @DisplayName("다른 클럽의 모임 id 로는 노트를 열 수 없다 — CLUB_MEETING_NOT_FOUND")
    void getRejectsForeignMeeting() {
        var activeClub = club(ClubStatus.ACTIVE);
        when(clubService.getClub(OTHER_CLUB)).thenReturn(activeClub);

        assertCode(() -> service.get(ME, OTHER_CLUB, MEETING_ID), ErrorCode.CLUB_MEETING_NOT_FOUND);
    }

    @Test
    @DisplayName("끝난 클럽·취소된 모임의 노트는 readOnly")
    void readOnlyFlags() {
        when(noteRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.empty());
        var endedClub = club(ClubStatus.ENDED);
        when(clubService.getClub(CLUB_ID)).thenReturn(endedClub);
        assertThat(service.get(ME, CLUB_ID, MEETING_ID).readOnly()).isTrue();

        var activeClub = club(ClubStatus.ACTIVE);
        when(clubService.getClub(CLUB_ID)).thenReturn(activeClub);
        var cancelledMeeting = meeting(CLUB_ID, false);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(cancelledMeeting));
        assertThat(service.get(ME, CLUB_ID, MEETING_ID).readOnly()).isTrue();
    }

    // ────────────────────────────── 연산 ──────────────────────────────

    @Test
    @DisplayName("연산을 적용하면 노트를 (없으면 만들고) 잠근 뒤 고치고 version 을 올려 방송한다")
    void applyOpsUpdatesAndBroadcasts() {
        ClubMeetingNote note = note(MeetingNoteOps.emptyDocument(), 4);
        when(noteRepository.findByMeetingIdForUpdate(MEETING_ID)).thenReturn(Optional.of(note));

        MeetingNoteOpsResult result = service.applyOps(ME, CLUB_ID, MEETING_ID, ops("a", "b"), "device-1");

        assertThat(result.version()).isEqualTo(5);
        assertThat(note.getElementCount()).isEqualTo(2);
        assertThat(note.getUpdatedBy()).isEqualTo(ME);
        verify(noteRepository).insertIfAbsent(CLUB_ID, MEETING_ID);
        verify(noteRepository).addContributor(NOTE_ID, ME);
        verify(rateLimiter).require(eq("meeting:note:ops:" + ME), anyInt(), any());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        MeetingNoteChanged changed = (MeetingNoteChanged) event.getValue();
        assertThat(changed.version()).isEqualTo(5);
        assertThat(changed.ops()).hasSize(2);
        assertThat(changed.clientId()).isEqualTo("device-1");
        assertThat(changed.by().userId()).isEqualTo(ME);
    }

    @Test
    @DisplayName("끝난 클럽의 노트에는 연산을 적용하지 않는다 — MEETING_NOTE_READ_ONLY")
    void applyOpsRejectsEndedClub() {
        var endedClub = club(ClubStatus.ENDED);
        when(clubService.getClub(CLUB_ID)).thenReturn(endedClub);

        assertCode(() -> service.applyOps(ME, CLUB_ID, MEETING_ID, ops("a"), null), ErrorCode.MEETING_NOTE_READ_ONLY);
        verify(noteRepository, never()).findByMeetingIdForUpdate(anyLong());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("취소된 모임의 노트에는 연산을 적용하지 않는다 — MEETING_NOTE_READ_ONLY")
    void applyOpsRejectsCancelledMeeting() {
        var cancelledMeeting = meeting(CLUB_ID, false);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(cancelledMeeting));

        assertCode(() -> service.applyOps(ME, CLUB_ID, MEETING_ID, ops("a"), null), ErrorCode.MEETING_NOTE_READ_ONLY);
    }

    @Test
    @DisplayName("멤버가 아니면 연산을 보낼 수 없다 — 잠그기 전에 거절")
    void applyOpsRejectsOutsider() {
        assertCode(() -> service.applyOps(OUTSIDER, CLUB_ID, MEETING_ID, ops("a"), null), ErrorCode.CLUB_NOT_MEMBER);
        verify(noteRepository, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("형식이 틀린 연산은 잠그기 전에 거절한다")
    void applyOpsRejectsMalformedBeforeLock() {
        assertCode(() -> service.applyOps(ME, CLUB_ID, MEETING_ID, List.of(Map.of("t", "nope")), null),
                ErrorCode.INVALID_REQUEST);
        verify(noteRepository, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("문서가 1MB 를 넘으면 MEETING_NOTE_TOO_LARGE — 노트는 그대로")
    void applyOpsRejectsHugeDocument() {
        ClubMeetingNote note = note(MeetingNoteOps.emptyDocument(), 1);
        when(noteRepository.findByMeetingIdForUpdate(MEETING_ID)).thenReturn(Optional.of(note));
        Map<String, Object> big = Map.of("id", "big", "type", "text", "text", "가".repeat(400_000));

        assertCode(() -> service.applyOps(ME, CLUB_ID, MEETING_ID, List.of(Map.of("t", "upsert", "el", big)), null),
                ErrorCode.MEETING_NOTE_TOO_LARGE);
        assertThat(note.getVersion()).isEqualTo(1);
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("문서가 참조한 같은 모임의 임시 사진은 노트에 붙이고, 빠진 사진은 뗀다")
    void applyOpsSyncsImages() {
        ClubMeetingNote note = note(MeetingNoteOps.emptyDocument(), 0);
        when(noteRepository.findByMeetingIdForUpdate(MEETING_ID)).thenReturn(Optional.of(note));
        ClubMeetingNoteImage stale = image(1L, MEETING_ID);
        stale.attach(NOTE_ID);
        ClubMeetingNoteImage fresh = image(2L, MEETING_ID);
        ClubMeetingNoteImage foreign = image(3L, 99L);
        when(imageRepository.findAllByNoteId(NOTE_ID)).thenReturn(List.of(stale));
        when(imageRepository.findAllById(any())).thenReturn(List.of(fresh, foreign));

        service.applyOps(ME, CLUB_ID, MEETING_ID, List.of(
                Map.of("t", "upsert", "el", Map.of("id", "p2", "type", "photo", "imageId", 2)),
                Map.of("t", "upsert", "el", Map.of("id", "p3", "type", "photo", "imageId", 3))), null);

        assertThat(stale.isDetached()).isTrue();
        assertThat(fresh.getNoteId()).isEqualTo(NOTE_ID);
        assertThat(foreign.isDetached()).isTrue();
    }

    private static ClubMeetingNoteImage image(long id, long meetingId) {
        ClubMeetingNoteImage image = ClubMeetingNoteImage.builder().clubId(CLUB_ID).meetingId(meetingId).userId(ME)
                .storageKey("k" + id).url("u").contentType("image/jpeg").byteSize(1).build();
        try {
            Field f = ClubMeetingNoteImage.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(image, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return image;
    }

    // ────────────────────────────── 마무리 ──────────────────────────────

    /** 모임을 연 사람이 creatorId 인 열린 모임. */
    private void givenMeetingCreatedBy(long creatorId) {
        var meeting = meeting(CLUB_ID, true);
        when(meeting.getCreatedBy()).thenReturn(creatorId);
        when(meetingRepository.findById(MEETING_ID)).thenReturn(Optional.of(meeting));
    }

    @Test
    @DisplayName("모임을 연 사람이 마무리하면 노트가 읽기만 되고, 커밋 뒤 방송할 마무리 이벤트를 낸다")
    void creatorClosesNote() {
        givenMeetingCreatedBy(ME);
        ClubMeetingNote note = note(MeetingNoteOps.emptyDocument(), 3);
        when(noteRepository.findByMeetingIdForUpdate(MEETING_ID)).thenReturn(Optional.of(note));

        MeetingNoteView view = service.close(ME, CLUB_ID, MEETING_ID);

        assertThat(note.isClosed()).isTrue();
        assertThat(view.readOnly()).isTrue();
        assertThat(view.closedAt()).isEqualTo(NOW);
        assertThat(view.canClose()).isFalse();
        verify(noteRepository).insertIfAbsent(CLUB_ID, MEETING_ID);
        verify(events).publishEvent(new ClubMeetingNoteService.MeetingNoteClosed(CLUB_ID, MEETING_ID));
    }

    @Test
    @DisplayName("모임을 연 사람이 아니어도 클럽 호스트는 마무리할 수 있다")
    void hostClosesNote() {
        givenMeetingCreatedBy(2L);
        var hostClub = club(ClubStatus.ACTIVE);
        when(hostClub.isHost(ME)).thenReturn(true);
        when(clubService.getClub(CLUB_ID)).thenReturn(hostClub);
        when(noteRepository.findByMeetingIdForUpdate(MEETING_ID)).thenReturn(Optional.of(note(MeetingNoteOps.emptyDocument(), 1)));

        assertThat(service.close(ME, CLUB_ID, MEETING_ID).readOnly()).isTrue();
    }

    @Test
    @DisplayName("모임을 연 사람도 호스트도 아니면 마무리할 수 없다 — FORBIDDEN")
    void memberCannotClose() {
        givenMeetingCreatedBy(2L);

        assertCode(() -> service.close(ME, CLUB_ID, MEETING_ID), ErrorCode.FORBIDDEN);
        verify(noteRepository, never()).insertIfAbsent(anyLong(), anyLong());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("마무리한 노트는 읽기만 되고 마무리 버튼도 없다, 연산은 MEETING_NOTE_READ_ONLY 로 거절한다")
    void closedNoteIsReadOnly() {
        givenMeetingCreatedBy(ME);
        ClubMeetingNote note = note(MeetingNoteOps.emptyDocument(), 2);
        note.close(ME, NOW);
        when(noteRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.of(note));
        when(noteRepository.findByMeetingIdForUpdate(MEETING_ID)).thenReturn(Optional.of(note));

        MeetingNoteView view = service.get(ME, CLUB_ID, MEETING_ID);
        assertThat(view.readOnly()).isTrue();
        assertThat(view.canClose()).isFalse();

        assertCode(() -> service.applyOps(ME, CLUB_ID, MEETING_ID, ops("a"), null), ErrorCode.MEETING_NOTE_READ_ONLY);
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("아직 쓸 수 있는 노트는 모임을 연 사람에게만 마무리 버튼을 연다")
    void canCloseOnlyForCreator() {
        when(noteRepository.findByMeetingId(MEETING_ID)).thenReturn(Optional.empty());
        givenMeetingCreatedBy(ME);
        assertThat(service.get(ME, CLUB_ID, MEETING_ID).canClose()).isTrue();

        givenMeetingCreatedBy(2L);
        assertThat(service.get(ME, CLUB_ID, MEETING_ID).canClose()).isFalse();
    }
}
