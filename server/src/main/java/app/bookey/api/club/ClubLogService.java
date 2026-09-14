package app.bookey.api.club;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.api.club.dto.ClubDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.ImageSniffer;
import app.bookey.common.storage.StorageKeys;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.reading.ReadingSession;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.reading.SessionTotals;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모임 읽기로그 — 독서 세션 끝에 남기는 조각(사진 한 장 + 한 줄)과 하루 보드.
 *
 * <p>조각은 {@code club_posts} 의 {@link ClubPostType#LOG} 라서 가림·반응·신고는 토론 글과 같은 규칙을 탄다.
 * 쪽에 붙인 조각은 그 쪽까지 읽은 멤버에게만 사진·본문이 내려간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubLogService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 하루에 남길 수 있는 조각 수 — 조각은 세션마다 하나라 넉넉히 둔다. */
    private static final int LOG_DAILY_LIMIT = 20;
    static final int BODY_MAX_LENGTH = 100;
    /** 요일 스트립이 한 번에 받는 최대 일수. */
    static final int MAX_DAY_RANGE = 14;
    private static final int SNIFF_BYTES = 64 * 1024;
    /** 주간 카드에 붙이는 대표 조각 수 — 카드 콜라주 칸 수와 같다. */
    static final int WEEK_HIGHLIGHTS = 6;

    private final ClubService clubService;
    private final ClubPostService postService;
    private final ClubPostRepository postRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubBookRepository clubBookRepository;
    private final BookRepository bookRepository;
    private final ReadingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final StorageService storage;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;
    private final Clock clock;

    /** 조각 남기기 요청 — 멀티파트의 글자 필드. */
    public record CreateLogCommand(String body, Integer anchorPage, SpoilerLevel spoilerLevel, Long readingSessionId) {}

    // ────────────────────────────── 남기기 ──────────────────────────────

    /**
     * 일부러 @Transactional 을 두지 않는다({@code PostImageService} 와 같은 이유) — 사진 업로드를
     * DB 트랜잭션 안에 두면 네트워크 시간 동안 커넥션을 붙든다. DB 쓰기는 마지막 save 하나라
     * 리포지토리 트랜잭션으로 충분하고, 저장이 실패하면 올린 파일을 바로 지운다.
     */
    public ClubPostView create(Long userId, Long clubId, CreateLogCommand command, MultipartFile file) {
        ClubMember me = clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        if (club.getStatus().isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }

        String body = command.body() == null ? "" : command.body().strip();
        boolean hasImage = file != null && !file.isEmpty();
        if (body.isEmpty() && !hasImage) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "사진이나 한 줄 중 하나는 남겨 주세요.");
        }
        if (body.length() > BODY_MAX_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "한 줄은 " + BODY_MAX_LENGTH + "자까지 쓸 수 있습니다.");
        }
        if (command.anchorPage() != null && command.anchorPage() < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "쪽 번호가 올바르지 않습니다.");
        }
        if (command.spoilerLevel() == SpoilerLevel.PAGE && command.anchorPage() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "쪽에 붙이려면 쪽 번호가 필요합니다.");
        }
        requireOwnSession(me, command.readingSessionId());
        // 사진을 받기 전에 레이트리밋을 먼저 건다 — 10MB 를 받아 놓고 거절하지 않도록.
        rateLimiter.require("club:log:" + userId, LOG_DAILY_LIMIT, Duration.ofDays(1));

        ClubPost.LogImage image = hasImage ? storeImage(clubId, userId, file) : null;
        Long clubBookId = clubBookRepository.findFirstByClubIdOrderBySeqAsc(clubId)
                .map(ClubBook::getId)
                .orElse(null);

        ClubPost saved;
        try {
            saved = postRepository.save(ClubPost.log(clubId, clubBookId, userId, body,
                    command.anchorPage(), command.spoilerLevel(), command.readingSessionId(), image));
        } catch (RuntimeException e) {
            if (image != null) {
                deleteOrphan(image.storageKey(), e);
            }
            throw e;
        }
        return postService.viewsFor(me, List.of(saved)).get(0);
    }

    /** 조각에 붙이는 세션은 내 것이면서 이 모임 책의 기록이어야 한다. */
    private void requireOwnSession(ClubMember me, Long sessionId) {
        if (sessionId == null) {
            return;
        }
        ReadingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null
                || !session.getUserId().equals(me.getUserId())
                || !session.getReadingRecordId().equals(me.getReadingRecordId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이 모임 책의 내 독서 세션만 붙일 수 있습니다.");
        }
    }

    private ClubPost.LogImage storeImage(Long clubId, Long userId, MultipartFile file) {
        if (!storage.enabled()) {
            throw ApiException.of(ErrorCode.STORAGE_DISABLED);
        }
        long size = file.getSize();
        if (size > properties.storage().image().maxBytes()) {
            throw ApiException.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageSniffer.ImageType type;
        try (InputStream in = file.getInputStream()) {
            type = ImageSniffer.sniff(in.readNBytes(SNIFF_BYTES));
        } catch (IOException e) {
            log.warn("조각 사진의 앞부분을 읽지 못했습니다: userId={}", userId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
        if (type == null) {
            throw ApiException.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        // 클라이언트 파일명은 쓰지 않는다 — 키는 모임·사용자·시각·UUID 로만 만든다.
        String key = StorageKeys.forClubLog(clubId, userId, clock.instant(), type.extension());
        try (InputStream in = file.getInputStream()) {
            String url = storage.store(key, in, size, type.contentType());
            return new ClubPost.LogImage(url, key, type.width(), type.height());
        } catch (IOException e) {
            log.warn("조각 사진을 읽지 못했습니다: userId={}", userId, e);
            throw ApiException.of(ErrorCode.STORAGE_ERROR);
        }
    }

    private void deleteOrphan(String key, RuntimeException cause) {
        log.warn("조각 저장 실패 — 올린 사진을 지웁니다: key={}", key);
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("보상 삭제도 실패 — 수동 정리가 필요합니다: key={}", key, e);
            cause.addSuppressed(e);
        }
    }

    // ────────────────────────────── 보드 ──────────────────────────────

    /** 하루 보드 — date 는 KST 날짜, 비우면 오늘. */
    public ClubLogDayView day(Long userId, Long clubId, LocalDate date) {
        ClubMember me = clubService.activeMember(clubId, userId);
        LocalDate day = date == null ? LocalDate.now(clock.withZone(KST)) : date;
        Instant from = day.atStartOfDay(KST).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(KST).toInstant();

        List<ClubPost> logs = postRepository.findLogs(clubId, from, to);
        return new ClubLogDayView(day, postService.viewsFor(me, logs), summarize(clubId, from, to, logs.size()));
    }

    /**
     * 주간 공유 카드 — weekOf 가 속한 주(월~일, KST). 비우면 이번 주.
     * 대표 조각은 보는 사람에게 가려지지 않은 것 중 사진 있는 조각 → 반응 많은 조각 → 먼저 남긴 조각 순으로 6개.
     * 카드는 이미지로 모임 밖에 공유되므로 가려진 조각·문장은 싣지 않는다.
     */
    public ClubLogWeekView week(Long userId, Long clubId, LocalDate weekOf) {
        ClubMember me = clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        LocalDate anyDay = weekOf == null ? LocalDate.now(clock.withZone(KST)) : weekOf;
        LocalDate monday = anyDay.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = monday.atStartOfDay(KST).toInstant();
        Instant to = monday.plusDays(7).atStartOfDay(KST).toInstant();

        List<ClubPost> logs = postRepository.findLogs(clubId, from, to);
        List<ClubPostView> highlights = postService.viewsFor(me, logs).stream()
                .filter(v -> !v.masked())
                .sorted(Comparator.comparing((ClubPostView v) -> v.imageUrl() == null)
                        .thenComparing(ClubPostView::reactionCount, Comparator.reverseOrder())
                        .thenComparing(ClubPostView::createdAt))
                .limit(WEEK_HIGHLIGHTS)
                .toList();
        String topQuote = postService.viewsFor(me, postRepository.findQuotesBetween(clubId, from, to)).stream()
                .filter(v -> !v.masked() && v.body() != null && !v.body().isBlank())
                .map(ClubPostView::body)
                .findFirst()
                .orElse(null);
        BookSummary book = clubBookRepository.findFirstByClubIdOrderBySeqAsc(clubId)
                .flatMap(cb -> bookRepository.findById(cb.getBookId()))
                .map(BookSummary::from)
                .orElse(null);

        return new ClubLogWeekView(monday, monday.plusDays(6), club.getName(), book,
                summarize(clubId, from, to, logs.size()), highlights, topQuote);
    }

    /** 기간 합산 — 진척 공개 멤버의 끝난 세션(쪽·시간·읽은 사람) + 조각 수. */
    private ClubLogSummary summarize(Long clubId, Instant from, Instant to, int logCount) {
        List<Long> recordIds = memberRepository.findAllByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE).stream()
                .filter(ClubMember::isShareProgress)
                .map(ClubMember::getReadingRecordId)
                .filter(Objects::nonNull)
                .toList();
        SessionTotals totals = recordIds.isEmpty()
                ? SessionTotals.empty()
                : sessionRepository.sumTotalsEndedBetween(recordIds, from, to);
        return new ClubLogSummary(totals.pagesRead(), totals.durationSec(), totals.readerCount(), logCount);
    }

    /** 요일 스트립 — from~to(포함) 날짜마다 조각 수. 조각이 없는 날도 0 으로 채운다. */
    public List<ClubLogDayCount> days(Long userId, Long clubId, LocalDate from, LocalDate to) {
        clubService.activeMember(clubId, userId);
        if (to.isBefore(from) || from.plusDays(MAX_DAY_RANGE - 1).isBefore(to)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "기간은 " + MAX_DAY_RANGE + "일까지 조회할 수 있습니다.");
        }
        Map<LocalDate, Long> counts = postRepository.findLogTimes(clubId,
                        from.atStartOfDay(KST).toInstant(), to.plusDays(1).atStartOfDay(KST).toInstant())
                .stream()
                .collect(Collectors.groupingBy(at -> LocalDate.ofInstant(at, KST), Collectors.counting()));
        return from.datesUntil(to.plusDays(1))
                .map(d -> new ClubLogDayCount(d, counts.getOrDefault(d, 0L).intValue()))
                .toList();
    }

    /**
     * 지금 읽는 중 — 열린 세션이 있는 멤버. 진척 비공개 멤버와 나는 뺀다.
     * 4시간 넘게 열린 세션은 정리 배치가 닫기 전이라도 읽는 중으로 치지 않는다.
     */
    public List<ReadingNowView> readingNow(Long userId, Long clubId) {
        clubService.activeMember(clubId, userId);
        Map<Long, ClubMember> byRecord = memberRepository.findAllByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream()
                .filter(m -> m.isShareProgress() && !m.getUserId().equals(userId) && m.getReadingRecordId() != null)
                .collect(Collectors.toMap(ClubMember::getReadingRecordId, Function.identity(), (a, b) -> a));
        if (byRecord.isEmpty()) {
            return List.of();
        }
        Instant staleBefore = clock.instant().minus(ReadingSession.MAX_SESSION);
        List<ReadingSession> open = sessionRepository
                .findAllByReadingRecordIdInAndEndedAtIsNull(List.copyOf(byRecord.keySet())).stream()
                .filter(s -> s.getStartedAt().isAfter(staleBefore))
                .sorted(Comparator.comparing(ReadingSession::getStartedAt))
                .toList();
        if (open.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository
                .findAllById(open.stream().map(ReadingSession::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return open.stream()
                .map(s -> {
                    User user = users.get(s.getUserId());
                    return new ReadingNowView(s.getUserId(),
                            user == null ? "알 수 없음" : user.getNickname(),
                            user == null ? null : user.getAvatarUrl(),
                            s.getStartedAt());
                })
                .toList();
    }
}
