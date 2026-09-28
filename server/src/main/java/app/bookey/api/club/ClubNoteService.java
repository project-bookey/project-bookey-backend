package app.bookey.api.club;

import app.bookey.api.club.dto.ClubNoteDtos.*;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.*;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 모임 노트북 — 모임당 한 권, 활성 멤버 누구나 함께 꾸미는 페이지들.
 *
 * <p>페이지 문서는 앱이 소유한 JSON 이라 서버는 요소 수·크기만 검사하고 내용은 해석하지 않는다. 다만 문서의 photo 요소가
 * 참조하는 {@code imageId} 는 읽어서 {@link ClubNoteImage} 의 page_id 를 맞춘다 — 참조가 끊긴 사진은 24시간 뒤 정리 배치가 지운다.
 *
 * <p>저장은 문서 전체 덮어쓰기 + version 낙관적 잠금이다. 버전이 어긋나면 409 를 돌려주고 앱이 최신을 받아 병합한 뒤 다시 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubNoteService {

    static final int MAX_PAGES = 30;
    static final int MAX_ELEMENTS = 300;
    static final int MAX_DOCUMENT_BYTES = 512 * 1024;
    /** 자동 저장 안전망 — 앱은 1.5초 디바운스로 보내므로 정상 사용에선 닿지 않는다. */
    static final int SAVE_RATE_LIMIT = 60;
    /** 도배 방지 — 1분에 30장(독후감 사진과 같다). */
    static final int UPLOAD_RATE_LIMIT = 30;
    private static final int SNIFF_BYTES = 64 * 1024;
    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final ClubService clubService;
    private final ClubRepository clubRepository;
    private final ClubNotePageRepository pageRepository;
    private final ClubNoteImageRepository imageRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubEventRepository eventRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ────────────────────────────── 조회 ──────────────────────────────

    /** 페이지 목록은 문서 없이 요약만 — 끝난 모임도 읽을 수는 있다(readOnly 로 알린다). */
    @Transactional(readOnly = true)
    public ClubNotebookView notebook(Long userId, Long clubId) {
        clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        List<ClubNotePageSummary> summaries = pageRepository.findSummaries(clubId);
        Map<Long, User> users = loadUsers(summaries.stream().map(ClubNotePageSummary::updatedBy).toList());
        List<ClubNotePageSummaryView> pages = summaries.stream()
                .map(s -> new ClubNotePageSummaryView(s.id(), s.seq(), s.title(), s.version(), s.elementCount(),
                        editor(s.updatedBy(), users), s.updatedAt()))
                .toList();
        return new ClubNotebookView(clubId, club.getStatus().isOver(), policy(), pages);
    }

    @Transactional(readOnly = true)
    public ClubNotePageView page(Long userId, Long clubId, Long pageId) {
        ClubMember me = clubService.activeMember(clubId, userId);
        return toPageView(findPage(clubId, pageId), me);
    }

    // ────────────────────────────── 쓰기 ──────────────────────────────

    /** 마지막 뒤에 새 페이지를 붙인다. 모임 행을 잠가 동시에 만들어도 seq 와 30장 상한이 어긋나지 않게 한다. */
    @Transactional
    public ClubNotePageView createPage(Long userId, Long clubId, CreateClubNotePageRequest request) {
        ClubMember me = clubService.activeMember(clubId, userId);
        Club club = clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        requireOpen(club);
        if (pageRepository.countByClubId(clubId) >= MAX_PAGES) {
            throw ApiException.of(ErrorCode.CLUB_NOTE_PAGE_LIMIT);
        }
        int seq = pageRepository.findMaxSeq(clubId) + 1;
        String title = request == null ? null : cleanTitle(request.title());
        ClubNotePage page = pageRepository.saveAndFlush(ClubNotePage.create(clubId, seq, title, userId));

        eventRepository.save(new ClubEvent(clubId, userId, ClubEventType.NOTE_PAGE_ADDED,
                Map.of("pageId", page.getId(), "seq", seq)));
        notifyNewPage(club, clubId, page, userId);
        return toPageView(page, me);
    }

    /**
     * 문서 전체 덮어쓰기. 잠그기 전에 싼 검사(요소 수·크기·레이트리밋)를 먼저 해서 큰 문서나 도배는 행을 잡지 않고 돌려보낸다.
     * 응답은 요약만이다 — 자동 저장마다 최대 512KB 문서를 되돌려 줄 이유가 없다.
     */
    @Transactional
    public ClubNotePageSummaryView savePage(Long userId, Long clubId, Long pageId, SaveClubNotePageRequest request) {
        clubService.activeMember(clubId, userId);
        requireOpen(clubService.getClub(clubId));
        int elementCount = elementCount(request.document());
        requireDocumentSize(request.document());
        rateLimiter.require("club:note:save:" + userId, SAVE_RATE_LIMIT, Duration.ofMinutes(1));

        ClubNotePage page = pageRepository.findByIdForUpdate(pageId)
                .filter(p -> p.belongsTo(clubId))
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOTE_PAGE_NOT_FOUND));
        if (page.getVersion() != request.version()) {
            throw ApiException.of(ErrorCode.CLUB_NOTE_CONFLICT);
        }
        page.overwrite(cleanTitle(request.title()), request.document(), elementCount, userId);
        syncImages(clubId, page, referencedImageIds(request.document()));
        pageRepository.saveAndFlush(page);
        return summaryOf(page);
    }

    /** 만든 사람이거나 호스트·운영자만 지운다. 사진은 페이지에서 떼기만 하고 파일은 정리 배치가 24시간 뒤 지운다. */
    @Transactional
    public void deletePage(Long userId, Long clubId, Long pageId) {
        ClubMember me = clubService.activeMember(clubId, userId);
        requireOpen(clubService.getClub(clubId));
        ClubNotePage page = findPage(clubId, pageId);
        if (!page.isCreatedBy(userId) && !me.canModerate()) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        imageRepository.detachAllByPageId(pageId);
        pageRepository.delete(page);
    }

    /**
     * 사진 업로드 — 페이지가 아니라 모임 단위로 받는다. 응답 id 를 문서의 photo 요소에 넣어 저장하면 그때 페이지에 붙는다.
     *
     * <p>일부러 @Transactional 을 두지 않는다({@code PostImageService} 와 같은 이유) — 파일 업로드(GCS 면 네트워크로 수 초)를
     * DB 트랜잭션 안에 두면 그동안 커넥션을 붙든다. DB 쓰기는 마지막 save 하나라 리포지토리 트랜잭션으로 충분하고,
     * 행 저장이 실패하면 올린 파일을 바로 지운다 — 행 없는 파일은 정리 배치도 못 찾는 고아가 되므로.
     */
    public ClubNoteImageView uploadImage(Long userId, Long clubId, MultipartFile file) {
        clubService.activeMember(clubId, userId);
        requireOpen(clubService.getClub(clubId));
        // 저장소가 꺼져 있으면 가장 먼저 거절한다 — 10MB 를 다 받아 놓고 버리지 않도록.
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        rateLimiter.require("club:note:image:" + userId, UPLOAD_RATE_LIMIT, Duration.ofMinutes(1));
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "업로드할 파일이 비어 있습니다.");
        }
        long size = file.getSize();
        if (size > properties.storage().image().maxBytes()) {
            throw ApiException.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageSniffer.ImageType type = ImageSniffer.sniff(readHead(file));
        if (type == null) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        // 클라이언트 파일명은 쓰지 않는다 — 키는 모임·사용자·시각·UUID 로만 만든다.
        String key = StorageKeys.forClubNote(clubId, userId, clock.instant(), type.extension());
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storage.store(key, in, size, type.contentType());
        } catch (IOException e) {
            log.warn("노트북 사진을 읽지 못했습니다: userId={}", userId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }

        ClubNoteImage image;
        try {
            image = imageRepository.save(ClubNoteImage.builder()
                    .clubId(clubId)
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
        return new ClubNoteImageView(image.getId(), image.getUrl(), image.getWidth(), image.getHeight());
    }

    // ────────────────────────────── 문서 검사 ──────────────────────────────

    /** elements 가 없으면 0, 배열이 아니거나 상한을 넘으면 거절. 요소 내부는 보지 않는다. */
    static int elementCount(Map<String, Object> document) {
        Object elements = document.get("elements");
        if (elements == null) {
            return 0;
        }
        if (!(elements instanceof List<?> list)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "elements 는 배열이어야 합니다.");
        }
        if (list.size() > MAX_ELEMENTS) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "요소는 " + MAX_ELEMENTS + "개까지 둘 수 있습니다.");
        }
        return list.size();
    }

    /** 문서의 요소들이 참조하는 사진 id — 숫자 imageId 만 모은다. 맵이 아닌 요소·id 없는 요소는 건너뛴다. */
    static Set<Long> referencedImageIds(Map<String, Object> document) {
        if (!(document.get("elements") instanceof List<?> list)) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (Object element : list) {
            if (element instanceof Map<?, ?> map && map.get("imageId") instanceof Number id) {
                ids.add(id.longValue());
            }
        }
        return ids;
    }

    private void requireDocumentSize(Map<String, Object> document) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(document);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "문서를 읽을 수 없습니다.");
        }
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw ApiException.of(ErrorCode.CLUB_NOTE_TOO_LARGE);
        }
    }

    /**
     * 페이지에 붙은 사진을 문서 참조와 맞춘다 — 빠진 건 떼고(24시간 뒤 배치가 지움), 새로 참조한 건 붙인다.
     * 다른 모임의 사진·이미 다른 페이지에 붙은 사진·모르는 id 는 조용히 무시한다. 문서는 불투명하고 사진 URL 은 어차피 공개라
     * 거절해 봐야 얻는 게 없고, 오히려 끊긴 참조 하나 때문에 자동 저장이 막히면 안 된다.
     */
    private void syncImages(Long clubId, ClubNotePage page, Set<Long> referenced) {
        Set<Long> attachedIds = new HashSet<>();
        for (ClubNoteImage image : imageRepository.findAllByPageId(page.getId())) {
            attachedIds.add(image.getId());
            if (!referenced.contains(image.getId())) {
                image.detach();
            }
        }
        List<Long> toAttach = referenced.stream().filter(id -> !attachedIds.contains(id)).toList();
        if (toAttach.isEmpty()) {
            return;
        }
        for (ClubNoteImage image : imageRepository.findAllById(toAttach)) {
            if (image.belongsTo(clubId) && image.isDetached()) {
                image.attach(page.getId());
            }
        }
    }

    // ────────────────────────────── 알림 ──────────────────────────────

    /** 새 페이지만 알린다 — 저장·삭제는 자동 저장 소음이라 알리지 않는다. 모임 스코프 일일 캡을 그대로 탄다. */
    private void notifyNewPage(Club club, Long clubId, ClubNotePage page, Long authorId) {
        for (ClubMember member : memberRepository.findAllByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)) {
            if (member.getUserId().equals(authorId)) {
                continue;
            }
            notificationService.schedule(new NotificationService.NotificationRequest(
                    member.getUserId(), NotificationType.CLUB_NOTE_PAGE, null, null, clubId,
                    club.getName(), "노트북에 새 페이지가 생겼어요",
                    Map.of("clubId", clubId, "pageId", page.getId()), null));
        }
    }

    // ────────────────────────────── 공통 ──────────────────────────────

    private ClubNotePage findPage(Long clubId, Long pageId) {
        return pageRepository.findById(pageId)
                .filter(p -> p.belongsTo(clubId))
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOTE_PAGE_NOT_FOUND));
    }

    private static void requireOpen(Club club) {
        if (club.getStatus().isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }
    }

    private static String cleanTitle(String title) {
        if (title == null) {
            return null;
        }
        String cleaned = title.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private ClubNotePolicy policy() {
        return new ClubNotePolicy(MAX_PAGES, MAX_ELEMENTS, MAX_DOCUMENT_BYTES,
                (int) properties.storage().image().maxBytes());
    }

    private ClubNotePageView toPageView(ClubNotePage page, ClubMember me) {
        Map<Long, User> users = loadUsers(Arrays.asList(page.getCreatedBy(), page.getUpdatedBy()));
        boolean canDelete = page.isCreatedBy(me.getUserId()) || me.canModerate();
        return new ClubNotePageView(page.getId(), page.getSeq(), page.getTitle(), page.getVersion(),
                page.getDocument(), page.getElementCount(),
                editor(page.getCreatedBy(), users), editor(page.getUpdatedBy(), users),
                page.getCreatedAt(), page.getUpdatedAt(), canDelete);
    }

    private ClubNotePageSummaryView summaryOf(ClubNotePage page) {
        Map<Long, User> users = loadUsers(List.of(page.getUpdatedBy()));
        return new ClubNotePageSummaryView(page.getId(), page.getSeq(), page.getTitle(), page.getVersion(),
                page.getElementCount(), editor(page.getUpdatedBy(), users), page.getUpdatedAt());
    }

    private Map<Long, User> loadUsers(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>();
        for (Long id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 탈퇴한 사용자(users 에 없음)는 닉네임을 "알 수 없음" 으로 채운다. id 자체가 없으면 null. */
    private static ClubNoteEditorView editor(Long userId, Map<Long, User> users) {
        if (userId == null) {
            return null;
        }
        User user = users.get(userId);
        if (user == null) {
            return new ClubNoteEditorView(userId, UNKNOWN_NICKNAME, null);
        }
        return new ClubNoteEditorView(userId, user.getNickname(), user.getAvatarUrl());
    }

    private void deleteOrphan(String key, RuntimeException cause) {
        log.warn("노트북 사진 행 저장 실패 — 올린 파일을 지웁니다: key={}", key);
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
            log.warn("노트북 사진의 앞부분을 읽지 못했습니다", e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }
}
