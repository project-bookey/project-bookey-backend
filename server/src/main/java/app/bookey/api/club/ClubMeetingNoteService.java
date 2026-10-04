package app.bookey.api.club;

import app.bookey.api.club.dto.ClubCommunityDtos.MeetingAttendeeView;
import app.bookey.api.club.dto.ClubMeetingNoteDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.*;
import app.bookey.domain.club.MeetingNoteOps.Op;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모임 공유 노트 — 모임(약속) 하나에 대형노트 한 권. 클럽 멤버 누구나 보고, 그 모임에 참여한 멤버가 함께 고친다.
 *
 * <p>쓰기는 요소 단위 연산뿐이다({@link MeetingNoteOps}). 실시간 연결(웹소켓)로 들어온 연산과 REST 로 들어온 연산이
 * 모두 {@link #applyOps} 하나를 탄다 — 연결이 끊긴 앱은 같은 연산을 REST 로 보내면 된다.
 * 적용이 커밋되면 {@link MeetingNoteChanged} 이벤트로 같은 노트를 보고 있는 모든 연결에 방송한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubMeetingNoteService {

    /** 연산 도배 방지 — 앱은 0.25초 간격으로 모아 보내므로 1분 240번이면 넉넉하다. */
    static final int OPS_RATE_LIMIT = 600;
    static final int UPLOAD_RATE_LIMIT = 30;
    static final int MAX_PAGE_SIZE = 30;
    private static final int SNIFF_BYTES = 64 * 1024;
    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final ClubService clubService;
    private final ClubMeetingRepository meetingRepository;
    private final ClubMeetingAttendeeRepository attendeeRepository;
    private final ClubMeetingNoteRepository noteRepository;
    private final ClubMeetingNoteImageRepository imageRepository;
    private final UserRepository userRepository;
    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** 노트를 마무리했다 — 커밋된 뒤 같은 노트를 보는 연결에 읽기 전용으로 바뀌었다고 알린다. */
    public record MeetingNoteClosed(Long clubId, Long meetingId) {
    }

    /** 연산이 커밋된 뒤 방송할 내용. ops 는 검증·정규화한 연산({t, el} / {t, id}) 이다. */
    public record MeetingNoteChanged(Long clubId, Long meetingId, int version, List<Map<String, Object>> ops,
                                     MeetingAttendeeView by, String clientId) {
    }

    /** 실시간 연결의 인증 결과 — 누가, 읽기 전용인지, 지금 노트 version(앱이 가진 것과 다르면 다시 받는다). */
    public record MeetingNoteAccess(MeetingAttendeeView me, boolean readOnly, int version) {
    }

    // ────────────────────────────── 조회 ──────────────────────────────

    /** 노트 한 권. 아직 아무도 쓰지 않았으면 빈 노트(id null, version 0)를 돌려준다 — 조회만으로 행을 만들지 않는다. */
    @Transactional(readOnly = true)
    public MeetingNoteView get(Long userId, Long clubId, Long meetingId) {
        clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        ClubMeeting meeting = findMeeting(clubId, meetingId);
        boolean mayClose = canClose(club, meeting, userId);
        boolean attending = attends(userId, meetingId);
        return noteRepository.findByMeetingId(meetingId)
                .map(note -> toView(note, meeting,
                        contributorsOf(List.of(note.getId())).getOrDefault(note.getId(), List.of()),
                        isReadOnly(club, meeting, note), mayClose, attending))
                .orElseGet(() -> {
                    boolean readOnly = isReadOnly(club, meeting, null);
                    return new MeetingNoteView(null, clubId, meetingId, meeting.getTitle(), meeting.getStartsAt(),
                            MeetingNoteOps.emptyDocument(), 0, 0, List.of(), null, readOnly || !attending, null,
                            mayClose && !readOnly, attending);
                });
    }

    /** 클럽 피드 — 빈 노트는 빼고 최근에 고친 순. 썸네일을 그리도록 문서를 함께 준다. */
    @Transactional(readOnly = true)
    public PageResponse<MeetingNoteView> listByClub(Long userId, Long clubId, int page, int size) {
        clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        Page<ClubMeetingNote> notes = noteRepository.findAllByClubIdAndElementCountGreaterThanOrderByUpdatedAtDesc(
                clubId, 0, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        List<Long> noteIds = notes.getContent().stream().map(ClubMeetingNote::getId).toList();
        Map<Long, ClubMeeting> meetings = meetingRepository.findAllById(
                        notes.getContent().stream().map(ClubMeetingNote::getMeetingId).toList()).stream()
                .collect(Collectors.toMap(ClubMeeting::getId, Function.identity()));
        Map<Long, List<MeetingAttendeeView>> contributors = contributorsOf(noteIds);
        Set<Long> attended = attendeeRepository.findAllById(meetings.keySet().stream()
                        .map(meetingId -> new ClubMeetingAttendee.Key(meetingId, userId)).toList()).stream()
                .map(ClubMeetingAttendee::getMeetingId).collect(Collectors.toSet());
        return PageResponse.of(notes, note -> {
            ClubMeeting meeting = meetings.get(note.getMeetingId());
            return toView(note, meeting, contributors.getOrDefault(note.getId(), List.of()),
                    isReadOnly(club, meeting, note), canClose(club, meeting, userId),
                    attended.contains(note.getMeetingId()));
        });
    }

    /** 실시간 연결 인증 — 클럽 활성 멤버만, 모임이 그 클럽 것이어야 한다. 모임에 참여하지 않았으면 읽기 전용 연결이다. */
    @Transactional(readOnly = true)
    public MeetingNoteAccess access(Long userId, Long clubId, Long meetingId) {
        clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        ClubMeeting meeting = findMeeting(clubId, meetingId);
        ClubMeetingNote note = noteRepository.findByMeetingId(meetingId).orElse(null);
        boolean readOnly = isReadOnly(club, meeting, note) || !attends(userId, meetingId);
        return new MeetingNoteAccess(personOf(userId), readOnly, note == null ? 0 : note.getVersion());
    }

    // ────────────────────────────── 쓰기 ──────────────────────────────

    /**
     * 연산 적용 — 모임에 참여한 멤버만. 싼 검사(멤버·참여·형식·레이트리밋)를 먼저 하고, 노트 행을 잠근 뒤 적용·크기 검사·사진 연결까지 한 트랜잭션에서 한다.
     * 노트가 없으면 여기서 처음 만든다.
     */
    @Transactional
    public MeetingNoteOpsResult applyOps(Long userId, Long clubId, Long meetingId, List<?> rawOps, String clientId) {
        clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        ClubMeeting meeting = findMeeting(clubId, meetingId);
        if (isReadOnly(club, meeting)) {
            throw ApiException.of(ErrorCode.MEETING_NOTE_READ_ONLY);
        }
        requireAttending(userId, meetingId);
        List<Op> ops = MeetingNoteOps.parse(rawOps);
        rateLimiter.require("meeting:note:ops:" + userId, OPS_RATE_LIMIT, Duration.ofMinutes(1));

        noteRepository.insertIfAbsent(clubId, meetingId);
        ClubMeetingNote note = noteRepository.findByMeetingIdForUpdate(meetingId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_MEETING_NOT_FOUND));
        // 행을 잠근 뒤 다시 본다 — 마무리와 동시에 들어온 연산이 마무리 뒤에 끼어들지 않게.
        if (note.isClosed()) {
            throw ApiException.of(ErrorCode.MEETING_NOTE_READ_ONLY);
        }
        Map<String, Object> next = MeetingNoteOps.apply(note.getDocument(), ops);
        requireDocumentSize(next);
        note.replace(next, MeetingNoteOps.elementCount(next), userId);
        syncImages(meetingId, note, MeetingNoteOps.referencedImageIds(next));
        noteRepository.saveAndFlush(note);
        noteRepository.addContributor(note.getId(), userId);

        events.publishEvent(new MeetingNoteChanged(clubId, meetingId, note.getVersion(), normalized(ops),
                personOf(userId), clientId));
        return new MeetingNoteOpsResult(note.getVersion());
    }

    /**
     * 사진 업로드 — 응답 id 를 photo 요소의 imageId 로 넣어 연산을 보내면 그때 노트에 붙는다.
     * 일부러 @Transactional 을 두지 않는다(PostImageService 와 같은 이유 — 파일 업로드 동안 커넥션을 붙들지 않도록).
     */
    public MeetingNoteImageView uploadImage(Long userId, Long clubId, Long meetingId, MultipartFile file) {
        clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        if (isReadOnly(club, findMeeting(clubId, meetingId), noteRepository.findByMeetingId(meetingId).orElse(null))) {
            throw ApiException.of(ErrorCode.MEETING_NOTE_READ_ONLY);
        }
        requireAttending(userId, meetingId);
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        rateLimiter.require("meeting:note:image:" + userId, UPLOAD_RATE_LIMIT, Duration.ofMinutes(1));
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "올릴 파일이 비어 있어요.");
        }
        long size = file.getSize();
        if (size > properties.storage().image().maxBytes()) {
            throw ApiException.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageSniffer.ImageType type = ImageSniffer.sniff(readHead(file));
        if (type == null) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        String key = StorageKeys.forMeetingNote(clubId, meetingId, userId, clock.instant(), type.extension());
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storage.store(key, in, size, type.contentType());
        } catch (IOException e) {
            log.warn("모임 노트 사진을 읽지 못했습니다: userId={}", userId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }

        ClubMeetingNoteImage image;
        try {
            image = imageRepository.save(ClubMeetingNoteImage.builder()
                    .clubId(clubId)
                    .meetingId(meetingId)
                    .userId(userId)
                    .storageKey(key)
                    .url(url)
                    .contentType(type.contentType())
                    .byteSize((int) size)
                    .width(type.width())
                    .height(type.height())
                    .build());
        } catch (RuntimeException e) {
            deleteOrphan(key, e);
            throw e;
        }
        return new MeetingNoteImageView(image.getId(), image.getUrl(), image.getWidth(), image.getHeight());
    }

    /**
     * 노트 마무리 — 모임을 연 사람(그 사람이 클럽을 떠났을 수 있어 호스트도)만. 마무리하면 모두 읽기만 되고,
     * 커밋된 뒤 같은 노트를 보는 연결에 알린다. 이미 마무리한 노트면 그대로 돌려준다.
     * 앱은 남은 편집을 다 보낸 뒤 부른다 — 마무리 뒤에 닿은 연산은 읽기 전용으로 거절된다.
     */
    @Transactional
    public MeetingNoteView close(Long userId, Long clubId, Long meetingId) {
        clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        ClubMeeting meeting = findMeeting(clubId, meetingId);
        if (!canClose(club, meeting, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "노트는 모임을 연 사람이나 호스트만 마무리할 수 있어요.");
        }
        if (isReadOnly(club, meeting)) {
            throw ApiException.of(ErrorCode.MEETING_NOTE_READ_ONLY);
        }
        noteRepository.insertIfAbsent(clubId, meetingId);
        ClubMeetingNote note = noteRepository.findByMeetingIdForUpdate(meetingId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_MEETING_NOT_FOUND));
        if (!note.isClosed()) {
            note.close(userId, clock.instant());
            noteRepository.saveAndFlush(note);
            events.publishEvent(new MeetingNoteClosed(clubId, meetingId));
        }
        return toView(note, meeting, contributorsOf(List.of(note.getId())).getOrDefault(note.getId(), List.of()),
                true, true, attends(userId, meetingId));
    }

    // ────────────────────────────── 문서 ──────────────────────────────

    private void requireDocumentSize(Map<String, Object> document) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(document);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "노트를 읽지 못했어요.");
        }
        if (bytes.length > MeetingNoteOps.MAX_DOCUMENT_BYTES) {
            throw ApiException.of(ErrorCode.MEETING_NOTE_TOO_LARGE);
        }
    }

    /**
     * 노트에 붙은 사진을 문서 참조와 맞춘다 — 빠진 건 떼고(24시간 뒤 배치가 지움), 새로 참조한 건 붙인다.
     * 다른 모임의 사진·이미 다른 노트에 붙은 사진·모르는 id 는 조용히 무시한다 — 끊긴 참조 하나로 편집이 막히면 안 된다.
     */
    private void syncImages(Long meetingId, ClubMeetingNote note, Set<Long> referenced) {
        Set<Long> attachedIds = new HashSet<>();
        for (ClubMeetingNoteImage image : imageRepository.findAllByNoteId(note.getId())) {
            attachedIds.add(image.getId());
            if (!referenced.contains(image.getId())) {
                image.detach();
            }
        }
        List<Long> toAttach = referenced.stream().filter(id -> !attachedIds.contains(id)).toList();
        if (toAttach.isEmpty()) {
            return;
        }
        for (ClubMeetingNoteImage image : imageRepository.findAllById(toAttach)) {
            if (image.belongsTo(meetingId) && image.isDetached()) {
                image.attach(note.getId());
            }
        }
    }

    private static List<Map<String, Object>> normalized(List<Op> ops) {
        List<Map<String, Object>> out = new ArrayList<>(ops.size());
        for (Op op : ops) {
            out.add(op.isDelete() ? Map.of("t", "delete", "id", op.id()) : Map.of("t", "upsert", "el", op.element()));
        }
        return out;
    }

    // ────────────────────────────── 공통 ──────────────────────────────

    private ClubMeeting findMeeting(Long clubId, Long meetingId) {
        return meetingRepository.findById(meetingId)
                .filter(m -> m.getClubId().equals(clubId))
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_MEETING_NOT_FOUND));
    }

    /** 끝난 클럽이나 취소된 모임의 노트는 읽기만 된다. */
    static boolean isReadOnly(Club club, ClubMeeting meeting) {
        return club.getStatus().isOver() || meeting == null || !meeting.isOpen();
    }

    /** 위에 더해 마무리한 노트도 읽기만 된다. 아직 행이 없는 노트(note null)는 마무리 전이다. */
    static boolean isReadOnly(Club club, ClubMeeting meeting, ClubMeetingNote note) {
        return isReadOnly(club, meeting) || (note != null && note.isClosed());
    }

    /** 마무리할 수 있는 사람 — 모임을 연 사람, 그리고 그 사람이 떠났을 때를 위해 클럽 호스트. */
    static boolean canClose(Club club, ClubMeeting meeting, Long userId) {
        return meeting != null && (userId.equals(meeting.getCreatedBy()) || club.isHost(userId));
    }

    private boolean attends(Long userId, Long meetingId) {
        return attendeeRepository.existsById(new ClubMeetingAttendee.Key(meetingId, userId));
    }

    /**
     * 노트는 모임에 참여한 사람만 쓴다. 읽기 전용 코드로 거절해 예전 앱도 편집기를 잠그게 한다
     * (편집 중에 참여를 취소한 사람도 다음 연산에서 잠긴다).
     */
    private void requireAttending(Long userId, Long meetingId) {
        if (!attends(userId, meetingId)) {
            throw new ApiException(ErrorCode.MEETING_NOTE_READ_ONLY, "모임에 참여한 사람만 노트를 쓸 수 있어요.");
        }
    }

    /**
     * 보기 값 — readOnly 는 노트 자체가 읽기만 되는지(끝난 클럽·취소된 모임·마무리)이고, 보는 사람이 참여하지 않았으면
     * 응답의 readOnly 도 켠다. 마무리는 노트 자체가 아직 쓸 수 있을 때만 — 이미 읽기만 되는 노트는 마무리할 것이 없다.
     */
    private MeetingNoteView toView(ClubMeetingNote note, ClubMeeting meeting, List<MeetingAttendeeView> contributors,
                                   boolean readOnly, boolean mayClose, boolean attending) {
        return new MeetingNoteView(note.getId(), note.getClubId(), note.getMeetingId(),
                meeting == null ? null : meeting.getTitle(), meeting == null ? null : meeting.getStartsAt(),
                note.getDocument(), note.getVersion(), note.getElementCount(), contributors, note.getUpdatedAt(),
                readOnly || !attending, note.getClosedAt(), mayClose && !readOnly, attending);
    }

    private Map<Long, List<MeetingAttendeeView>> contributorsOf(Collection<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> pairs = noteRepository.findContributorPairs(noteIds);
        Set<Long> userIds = pairs.stream().map(p -> ((Number) p[1]).longValue()).collect(Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<MeetingAttendeeView>> out = new HashMap<>();
        for (Object[] pair : pairs) {
            long noteId = ((Number) pair[0]).longValue();
            long userId = ((Number) pair[1]).longValue();
            User user = users.get(userId);
            if (user != null && user.getStatus() == UserStatus.TERMINATED) {
                continue;   // 노트는 함께 만든 것이라 남기되, 탈퇴한 사람은 참여자로 보여 주지 않는다
            }
            out.computeIfAbsent(noteId, k -> new ArrayList<>()).add(person(userId, user));
        }
        return out;
    }

    private MeetingAttendeeView personOf(Long userId) {
        return person(userId, userRepository.findById(userId).orElse(null));
    }

    /** 탈퇴한 사용자는 닉네임을 "알 수 없음" 으로 채운다. */
    private static MeetingAttendeeView person(Long userId, User user) {
        return user == null
                ? new MeetingAttendeeView(userId, UNKNOWN_NICKNAME, null)
                : new MeetingAttendeeView(userId, user.getNickname(), user.getAvatarUrl());
    }

    private void deleteOrphan(String key, RuntimeException cause) {
        log.warn("모임 노트 사진 행 저장 실패 — 올린 파일을 지웁니다: key={}", key);
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("보상 삭제도 실패 — 수동 정리가 필요합니다: key={}", key, e);
            cause.addSuppressed(e);
        }
    }

    /** 파일 앞 최대 64KB — 매직넘버·크기 헤더는 이 안에 있다. */
    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            log.warn("모임 노트 사진의 앞부분을 읽지 못했습니다", e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }
}
