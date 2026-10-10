package app.bookey.api.club;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.api.club.dto.ClubDtos.*;
import app.bookey.api.library.ProgressService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.JoinCodeGenerator;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.admin.OpsFlag;
import app.bookey.domain.admin.OpsFlagRepository;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.reading.*;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Objects;
import java.time.Instant;
import app.bookey.domain.reading.ReadingSession;

/** 독서 모임 (§F12). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_CODE_ATTEMPTS = 10;
    /** 모임 대량 생성 방지 (§8.5). */
    private static final int CLUB_CREATE_DAILY_LIMIT = 5;

    private final ClubRepository clubRepository;
    private final ClubBookRepository clubBookRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubMeetingRepository meetingRepository;
    private final ClubPostRepository postRepository;
    private final ClubEventRepository eventRepository;
    private final ClubChatUnlockRepository chatUnlockRepository;
    private final ClubChatReadRepository chatReadRepository;
    private final ClubMeetingAttendeeRepository attendeeRepository;
    private final BookRepository bookRepository;
    private final ReadingRecordRepository recordRepository;
    private final ReadingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final OpsFlagRepository opsFlagRepository;
    private final ProgressService progressService;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;

    // ────────────────────────────── 생성 ──────────────────────────────

    /**
     * 모임은 기간 없이 이어지고 책은 만남마다 고른다 — 이름 · 공개 범위만으로 연다.
     * 정원은 늘 무료 정원이고, 더 필요하면 만든 뒤 책갈피로 늘린다.
     * 처음 읽을 책을 함께 주면 그 책을 지금 읽는 책으로 바로 잡는다.
     */
    @Transactional
    public ClubHomeView create(Long userId, CreateClubRequest request) {
        requireOpsEnabled(OpsFlag.CLUB_CREATION_OPEN, "지금은 클럽을 만들 수 없어요.");
        rateLimiter.require("club:create:" + userId, CLUB_CREATE_DAILY_LIMIT, Duration.ofDays(1));

        // 옛 앱이 보내는 더 작은 정원(2·3명)은 쓰지 않는다 — 무료 정원보다 작게 열면 늘릴 때 그만큼 책갈피를 더 쓰게 된다.
        int freeLimit = properties.club().freeMemberLimit();
        if (request.memberLimit() != null && request.memberLimit() > freeLimit) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "클럽은 " + freeLimit + "명으로 시작해요. 더 필요하면 클럽을 만든 뒤 자리를 늘려 주세요.");
        }

        Book book = request.bookId() == null
                ? null
                : bookRepository.findById(request.bookId())
                        .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));

        Club club = clubRepository.save(Club.builder()
                .ownerId(userId)
                .name(request.name())
                .description(request.description())
                .coverUrl(book == null ? null : book.getCoverUrl())
                .joinCode(generateUniqueCode())
                .visibility(request.visibility())
                .memberLimit((short) freeLimit)
                .startsAt(LocalDate.now(KST))
                .allowNudge(request.allowNudge() == null || request.allowNudge())
                .build());

        // 호스트도 멤버로 참가한다. 읽기 기록은 지금 읽는 책이 잡힐 때 잇는다.
        memberRepository.save(ClubMember.builder()
                .clubId(club.getId())
                .userId(userId)
                .role(ClubRole.HOST)
                .shareProgress(true)
                .allowNudge(true)
                .build());
        club.joinMember();
        if (book != null) {
            assignCurrentBook(club, book);
        }

        eventRepository.save(new ClubEvent(club.getId(), userId, ClubEventType.CREATED,
                book == null
                        ? Map.of("name", club.getName())
                        : Map.of("bookId", book.getId(), "name", club.getName())));

        return home(userId, club.getId());
    }

    // ────────────────────────────── 지금 읽는 책 ──────────────────────────────

    /**
     * 만남 일정에 맞춰 지금 읽는 책을 맞춘다 — 만남을 열고 · 고치고 · 취소할 때와 매일 새벽 배치가 부른다.
     * 책을 고른 만남이 없으면 지금 책을 그대로 둔다({@link ClubCurrentBook}).
     */
    @Transactional
    public void syncCurrentBook(Long clubId) {
        Club club = getClub(clubId);
        if (club.getStatus().isOver()) {
            return;
        }
        List<ClubCurrentBook.Slot> slots = meetingRepository.findAllByClubIdOrderByStartsAtAsc(clubId).stream()
                .map(ClubCurrentBook.Slot::of)
                .toList();
        Long bookId = ClubCurrentBook.pick(slots, LocalDate.now(KST), KST);
        if (bookId == null || bookId.equals(currentBookId(club))) {
            return;
        }
        bookRepository.findById(bookId).ifPresent(book -> assignCurrentBook(club, book));
    }

    /**
     * 지금 읽는 책을 바꾸고 멤버마다 그 책의 읽기 기록을 잇는다 — 기록이 없으면 서재에 '읽고 싶은'으로 넣는다
     * (참가 때 모임 책을 서재에 넣던 §12.1 ① 과 같은 규칙). 진척 · 스포일러 가림 · 지금 읽는 중이 이 기록을 본다.
     */
    private void assignCurrentBook(Club club, Book book) {
        ClubBook clubBook = clubBookRepository.findFirstByClubIdAndBookId(club.getId(), book.getId())
                .orElseGet(() -> clubBookRepository.save(ClubBook.builder()
                        .clubId(club.getId())
                        .bookId(book.getId())
                        .seq((short) (clubBookRepository.findFirstByClubIdOrderBySeqDesc(club.getId())
                                .map(ClubBook::getSeq).orElse((short) 0) + 1))
                        .totalPagesSnapshot(book.getTotalPages())
                        .build()));
        club.changeCurrentBook(clubBook.getId());
        for (ClubMember member : memberRepository.findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE)) {
            member.linkReadingRecord(ensureReadingRecord(member.getUserId(), book, null, false).getId());
        }
    }

    private Long currentBookId(Club club) {
        return club.getCurrentClubBookId() == null
                ? null
                : clubBookRepository.findById(club.getCurrentClubBookId()).map(ClubBook::getBookId).orElse(null);
    }

    /** 지금 읽는 책 — 책을 고른 만남이 아직 없으면 null. */
    private Book currentBook(Club club) {
        Long bookId = currentBookId(club);
        return bookId == null ? null : bookRepository.findById(bookId).orElse(null);
    }

    /** 다음 만남 — 취소되지 않은, 아직 시작하지 않은 만남. */
    private Optional<ClubMeeting> nextMeeting(Long clubId) {
        return meetingRepository
                .findFirstByClubIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(clubId, "OPEN", Instant.now());
    }

    private Instant nextMeetingAt(Long clubId) {
        return nextMeeting(clubId).map(ClubMeeting::getStartsAt).orElse(null);
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = JoinCodeGenerator.generate();
            if (!clubRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw ApiException.of(ErrorCode.INTERNAL_ERROR);
    }

    // ────────────────────────────── 참가 ──────────────────────────────

    /** 코드 조회는 레이트리밋을 건다 — 무작위 대입 방어 (§8.5). */
    @Transactional(readOnly = true)
    public ClubPreview preview(Long userId, String rawCode, String clientKey) {
        rateLimiter.require("club:code:" + clientKey,
                properties.club().joinCodeLookupRateLimit(), Duration.ofMinutes(1));

        String code = JoinCodeGenerator.normalize(rawCode);
        if (!JoinCodeGenerator.isValidFormat(code)) {
            throw ApiException.of(ErrorCode.CLUB_CODE_INVALID);
        }
        Club club = clubRepository.findByJoinCode(code)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_CODE_INVALID));
        return toPreview(club, userId);
    }

    @Transactional(readOnly = true)
    public ClubPreview previewById(Long userId, Long clubId) {
        Club club = getClub(clubId);
        if (club.getVisibility() == ClubVisibility.CODE_ONLY
                && memberRepository.findByClubIdAndUserId(clubId, userId).isEmpty()) {
            throw ApiException.of(ErrorCode.CLUB_NOT_FOUND);
        }
        return toPreview(club, userId);
    }

    private ClubPreview toPreview(Club club, Long userId) {
        Book book = currentBook(club);
        User host = userRepository.findById(club.getOwnerId()).orElse(null);

        Optional<ClubMember> membership = memberRepository.findByClubIdAndUserId(club.getId(), userId);
        boolean alreadyMember = membership.map(ClubMember::isActive).orElse(false);

        String blockedReason = null;
        if (membership.map(m -> m.getStatus() == ClubMemberStatus.KICKED).orElse(false)) {
            blockedReason = "다시 참가할 수 없는 클럽이에요.";
        } else if (club.getStatus().isOver()) {
            blockedReason = "이미 끝난 클럽이에요.";
        } else if (club.isFull()) {
            blockedReason = "정원이 다 찼어요.";
        }

        return new ClubPreview(
                club.getId(), club.getName(), club.getDescription(),
                book == null ? null : BookSummary.from(book),
                host == null ? null : host.getNickname(),
                club.getMemberCount(), club.getMemberLimit(),
                club.getStartsAt(), club.getEndsAt(), club.getStatus(),
                alreadyMember, blockedReason == null && !alreadyMember, blockedReason, club.getBackgroundUrl());
    }

    @Transactional
    public ClubHomeView join(Long userId, JoinRequest request) {
        String code = JoinCodeGenerator.normalize(request.code());
        // 정원 검사 전에 모임 행을 잠근다 — 동시 참가가 정원을 넘지 않게.
        Club club = clubRepository.findByJoinCodeForUpdate(code)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_CODE_INVALID));
        return joinClub(userId, club, request.adoptTargetDate(), request.shareProgress());
    }

    @Transactional
    public ClubHomeView joinPublic(Long userId, Long clubId, JoinPublicRequest request) {
        Club club = clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        if (club.getVisibility() != ClubVisibility.PUBLIC) {
            throw ApiException.of(ErrorCode.CLUB_NOT_FOUND);
        }
        return joinClub(userId, club, request.adoptTargetDate(), request.shareProgress());
    }

    private ClubHomeView joinClub(Long userId, Club club, Boolean adoptTargetDate, Boolean shareProgressRequest) {
        if (club.getStatus().isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }
        Optional<ClubMember> existing = memberRepository.findByClubIdAndUserId(club.getId(), userId);
        if (existing.isPresent()) {
            ClubMember member = existing.get();
            if (member.getStatus() == ClubMemberStatus.KICKED) {
                throw ApiException.of(ErrorCode.CLUB_KICKED);
            }
            if (member.isActive()) {
                throw ApiException.of(ErrorCode.CLUB_ALREADY_JOINED);
            }
        }

        // 지금 읽는 책이 있으면 서재에 넣고 그 기록을 잇는다. 모임에 기간이 없어 목표일은 건드리지 않는다.
        Book book = currentBook(club);
        Long recordId = book == null ? null : ensureReadingRecord(userId, book, null, false).getId();
        boolean shareProgress = shareProgressRequest == null || shareProgressRequest;

        club.joinMember();   // 정원·종료 검사 포함
        existing.ifPresentOrElse(
                member -> {
                    member.rejoin(recordId);
                    member.updateSharing(shareProgress, true);
                },
                () -> memberRepository.save(ClubMember.builder()
                        .clubId(club.getId())
                        .userId(userId)
                        .readingRecordId(recordId)
                        .role(ClubRole.MEMBER)
                        .shareProgress(shareProgress)
                        .allowNudge(true)
                        .build()));

        eventRepository.save(new ClubEvent(club.getId(), userId, ClubEventType.JOINED, Map.of()));
        return home(userId, club.getId());
    }

    /**
     * 참가 시 해당 도서를 서재에 자동 등록한다 (§12.1 참가 플로우 ①).
     * 이미 읽고 있는 책이면 기존 기록을 재사용한다.
     */
    private ReadingRecord ensureReadingRecord(Long userId, Book book, LocalDate targetDate,
                                              boolean adoptTarget) {
        List<ReadingRecord> records =
                recordRepository.findAllByUserIdAndBookIdOrderByRoundDesc(userId, book.getId());
        ReadingRecord open = records.stream()
                .filter(r -> !r.getStatus().isClosed())
                .findFirst()
                .orElse(null);

        if (open != null) {
            if (adoptTarget && open.getTargetFinishDate() == null) {
                open.changeTargetDate(targetDate);
            }
            return open;
        }
        short round = records.isEmpty() ? 1 : (short) (records.get(0).getRound() + 1);
        return recordRepository.save(ReadingRecord.builder()
                .userId(userId)
                .bookId(book.getId())
                .round(round)
                .status(ReadingStatus.WANT_TO_READ)
                .targetFinishDate(adoptTarget ? targetDate : null)
                .build());
    }

    @Transactional
    public void leave(Long userId, Long clubId) {
        Club club = getClub(clubId);
        ClubMember member = activeMember(clubId, userId);
        if (member.getRole() == ClubRole.HOST && club.getMemberCount() > 1) {
            throw ApiException.of(ErrorCode.CLUB_HOST_CANNOT_LEAVE);
        }
        member.leave();
        club.leaveMember();
        forgetMemberTraces(clubId, userId);
        eventRepository.save(new ClubEvent(clubId, userId, ClubEventType.LEFT, Map.of()));
        if (club.getMemberCount() == 0) {
            club.end();
        }
    }

    @Transactional
    public void kick(Long userId, Long clubId, KickRequest request) {
        Club club = getClub(clubId);
        requireHost(club, userId);
        if (request.userId().equals(userId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "나 자신은 내보낼 수 없어요.");
        }
        ClubMember target = activeMember(clubId, request.userId());
        target.kick(request.reason());
        club.leaveMember();
        forgetMemberTraces(clubId, request.userId());
        eventRepository.save(new ClubEvent(clubId, request.userId(), ClubEventType.KICKED,
                Map.of("reason", request.reason())));
    }

    /**
     * 나간(내보내진) 멤버의 흔적을 지운다 — 다시 참가하면 새 멤버로 시작한다.
     * 채팅 이용권(다시 열려면 책갈피를 다시 낸다)과 읽음 위치, 아직 시작하지 않은 모임의 참여를 지운다.
     * 채팅에 남긴 메시지는 그대로 두고, 보여 줄 때 '나간 멤버'로 바꾼다(ClubCommunityService.chatMessages).
     * 메모(조각)는 그대로 남는다(사용자 결정, 2026-10-05).
     */
    private void forgetMemberTraces(Long clubId, Long userId) {
        chatUnlockRepository.deleteByClubIdAndUserId(clubId, userId);
        chatReadRepository.deleteByClubIdAndUserId(clubId, userId);
        attendeeRepository.deleteUpcomingByClubIdAndUserId(clubId, userId, Instant.now());
    }

    /**
     * 탈퇴 — 참가한 클럽을 모두 나간다. 30일 뒤 계정이 지워질 때까지 기다리면 그동안 멤버 목록·자리·호스트 자리에
     * 탈퇴한 사람이 남는다. 호스트였으면 남은 멤버에게 넘기고(운영진 → 먼저 들어온 사람, AccountDeletionJob 과 같은 순서),
     * 남은 멤버가 없으면 클럽을 끝낸다. 이미 나간 클럽은 건드리지 않으므로 여러 번 불러도 된다.
     */
    @Transactional
    public void leaveAllOnWithdrawal(Long userId) {
        for (ClubMember member : memberRepository.findAllByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE)) {
            Club club = clubRepository.findByIdForUpdate(member.getClubId()).orElse(null);
            if (club == null) {
                continue;
            }
            if (club.isHost(userId)) {
                successorOf(club.getId(), userId).ifPresent(next -> {
                    next.changeRole(ClubRole.HOST);
                    member.changeRole(ClubRole.MEMBER);
                    club.transferHost(next.getUserId());
                });
            }
            member.leave();
            club.leaveMember();
            forgetMemberTraces(club.getId(), userId);
            eventRepository.save(new ClubEvent(club.getId(), userId, ClubEventType.LEFT, Map.of()));
            if (club.getMemberCount() == 0) {
                club.end();
            }
        }
    }

    /** 호스트 자리를 이어받을 멤버 — 탈퇴하지 않은 활성 멤버 중 운영진 먼저, 그다음 먼저 들어온 사람. */
    private Optional<ClubMember> successorOf(Long clubId, Long leavingUserId) {
        List<ClubMember> others = memberRepository.findAllByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE).stream()
                .filter(m -> !m.getUserId().equals(leavingUserId))
                .toList();
        Set<Long> terminated = loadUsers(others).values().stream()
                .filter(user -> user.getStatus() == UserStatus.TERMINATED)
                .map(User::getId)
                .collect(Collectors.toSet());
        return others.stream()
                .filter(m -> !terminated.contains(m.getUserId()))
                .min(Comparator.comparing(ClubMember::getRole)
                        .thenComparing(ClubMember::getJoinedAt)
                        .thenComparing(ClubMember::getId));
    }

    @Transactional
    public void transferHost(Long userId, Long clubId, TransferHostRequest request) {
        Club club = getClub(clubId);
        requireHost(club, userId);
        ClubMember newHost = activeMember(clubId, request.userId());
        ClubMember oldHost = activeMember(clubId, userId);
        newHost.changeRole(ClubRole.HOST);
        oldHost.changeRole(ClubRole.MEMBER);
        club.transferHost(request.userId());
    }

    @Transactional
    public ClubHomeView update(Long userId, Long clubId, UpdateClubRequest request) {
        Club club = getClub(clubId);
        requireHost(club, userId);
        club.update(request.name(), request.description(), request.visibility(), request.allowNudge());
        return home(userId, clubId);
    }

    /** 배경 사진 바꾸기(url·key 가 null 이면 빼기) — 호스트만. 이전 사진의 저장소 키를 돌려준다(지우는 건 호출자). */
    @Transactional
    public String changeBackground(Long userId, Long clubId, String url, String key) {
        Club club = getClub(clubId);
        requireHost(club, userId);
        return club.changeBackground(url, key);
    }

    @Transactional
    public String rotateJoinCode(Long userId, Long clubId) {
        Club club = getClub(clubId);
        requireHost(club, userId);
        String code = generateUniqueCode();
        club.rotateJoinCode(code);
        return code;
    }

    @Transactional
    public void updateSharing(Long userId, Long clubId, UpdateSharingRequest request) {
        ClubMember member = activeMember(clubId, userId);
        member.updateSharing(request.shareProgress(), request.allowNudge());
    }

    // ────────────────────────────── 관리자 ──────────────────────────────

    /**
     * 관리자 강퇴 — 호스트와 같은 정리(채팅 이용권·읽음 위치·다가올 모임 참여)를 한다.
     * 호스트는 내보낼 수 없다 — 먼저 다른 멤버에게 넘긴다.
     */
    @Transactional
    public void adminKick(Long clubId, Long userId, String reason) {
        Club club = getClub(clubId);
        if (club.isHost(userId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "호스트는 내보낼 수 없습니다. 먼저 다른 멤버에게 호스트를 넘기세요.");
        }
        ClubMember target = activeMember(clubId, userId);
        target.kick(reason);
        club.leaveMember();
        forgetMemberTraces(clubId, userId);
        eventRepository.save(new ClubEvent(clubId, userId, ClubEventType.KICKED,
                Map.of("reason", reason, "byAdmin", true)));
    }

    /** 관리자 호스트 승계 — 호스트 장기 미접속 대응. 로그인할 수 없는(정지·탈퇴) 회원에게는 넘기지 않는다. */
    @Transactional
    public void adminTransferHost(Long clubId, Long newOwnerId) {
        Club club = getClub(clubId);
        if (club.isHost(newOwnerId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이미 호스트인 멤버입니다.");
        }
        boolean usable = userRepository.findStatusById(newOwnerId).map(UserStatus::canLogin).orElse(false);
        if (!usable) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이용이 정지됐거나 탈퇴한 회원에게는 호스트를 넘길 수 없습니다.");
        }
        ClubMember newHost = activeMember(clubId, newOwnerId);
        memberRepository.findByClubIdAndUserId(clubId, club.getOwnerId())
                .ifPresent(old -> old.changeRole(ClubRole.MEMBER));
        newHost.changeRole(ClubRole.HOST);
        club.transferHost(newOwnerId);
    }

    /** 관리자 강제 종료 — 호스트가 끝낸 것과 같은 종료 기록을 남긴다(관리자 표시). */
    @Transactional
    public void adminEnd(Long clubId) {
        Club club = getClub(clubId);
        if (club.getStatus().isOver()) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 끝난 모임입니다.");
        }
        club.end();
        eventRepository.save(new ClubEvent(clubId, club.getOwnerId(), ClubEventType.ENDED, Map.of("byAdmin", true)));
    }

    @Transactional
    public void end(Long userId, Long clubId) {
        Club club = getClub(clubId);
        requireHost(club, userId);
        club.end();
        eventRepository.save(new ClubEvent(clubId, userId, ClubEventType.ENDED, Map.of()));
    }

    // ────────────────────────────── 조회 ──────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ClubSummaryView> myClubs(Long userId, Pageable pageable) {
        Page<ClubMember> memberships = memberRepository.findMyClubs(userId, pageable);
        List<Long> clubIds = memberships.getContent().stream().map(ClubMember::getClubId).toList();
        if (clubIds.isEmpty()) {
            return PageResponse.of(memberships.map(m -> null));
        }
        Map<Long, Club> clubs = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));
        // 지금 읽는 책 — 모임마다 club_books 한 줄을 가리킨다.
        Map<Long, ClubBook> clubBooks = clubBookRepository.findAllById(clubs.values().stream()
                        .map(Club::getCurrentClubBookId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(ClubBook::getId, Function.identity()));
        Map<Long, Book> books = bookRepository
                .findAllById(clubBooks.values().stream().map(ClubBook::getBookId).toList()).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        // 내가 참여한 다가오는 만남 — 클럽마다 가장 가까운 것 하나(이른 순으로 오므로 처음 본 것).
        Map<Long, ClubMeeting> myNext = new HashMap<>();
        for (ClubMeeting meeting : meetingRepository.findAttendingUpcoming(userId, clubIds, Instant.now())) {
            myNext.putIfAbsent(meeting.getClubId(), meeting);
        }

        LocalDate today = LocalDate.now(KST);
        return PageResponse.of(memberships, membership -> {
            Club club = clubs.get(membership.getClubId());
            ClubBook clubBook = club.getCurrentClubBookId() == null ? null : clubBooks.get(club.getCurrentClubBookId());
            Book book = clubBook == null ? null : books.get(clubBook.getBookId());
            List<ClubMember> peers = memberRepository
                    .findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE);
            Map<Long, ReadingRecord> records = loadRecords(peers);

            Double mine = completionRate(records.get(membership.getReadingRecordId()), book);
            Double average = averageCompletion(peers, records, book);
            List<ClubMemberBrief> briefs = memberBriefs(peers, records, book, userId);
            Optional<ClubMeeting> next = nextMeeting(club.getId());
            Optional<ClubMeeting> mineNext = Optional.ofNullable(myNext.get(club.getId()));

            return new ClubSummaryView(
                    club.getId(), club.getName(), club.getCoverUrl(),
                    book == null ? null : BookSummary.from(book),
                    club.getStatus(), club.getMemberCount(), club.daysLeft(today),
                    mine, average, 0, membership.getRole(), briefs,
                    next.map(ClubMeeting::getStartsAt).orElse(null), next.map(ClubMeeting::getTitle).orElse(null),
                    mineNext.map(ClubMeeting::getStartsAt).orElse(null), mineNext.map(ClubMeeting::getTitle).orElse(null),
                    club.getDescription(), club.getBackgroundUrl());
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<ClubPreview> publicClubs(Long userId, Pageable pageable) {
        return PageResponse.of(clubRepository.findPublicClubs(pageable), club -> toPreview(club, userId));
    }

    @Transactional(readOnly = true)
    public ClubHomeView home(Long userId, Long clubId) {
        Club club = getClub(clubId);
        ClubMember me = activeMember(clubId, userId);
        Book book = currentBook(club);

        List<ClubMember> members =
                memberRepository.findAllByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);
        Map<Long, ReadingRecord> records = loadRecords(members);
        Map<Long, User> users = loadUsers(members);

        List<MemberProgressView> memberViews = members.stream()
                .map(m -> toMemberView(m, users.get(m.getUserId()), records.get(m.getReadingRecordId()),
                        book, userId, club.isAllowNudge()))
                .sorted(Comparator.comparing(
                        (MemberProgressView v) -> v.completionRate() == null ? -1.0 : v.completionRate())
                        .reversed())
                .toList();

        int myRank = 1;
        Double myRate = completionRate(records.get(me.getReadingRecordId()), book);
        if (myRate != null) {
            myRank = (int) memberViews.stream()
                    .filter(v -> v.completionRate() != null && v.completionRate() > myRate)
                    .count() + 1;
        }

        return new ClubHomeView(
                club.getId(), club.getName(), club.getDescription(), club.getCoverUrl(),
                club.getJoinCode(), club.getVisibility(), club.getStatus(),
                book == null ? null : BookSummary.from(book),
                club.getStartsAt(), club.getEndsAt(), club.daysLeft(LocalDate.now(KST)),
                club.getMemberCount(), club.getMemberLimit(),
                me.getRole(), me.isShareProgress(), me.isAllowNudge(),
                myRank, averageCompletion(members, records, book),
                memberViews, List.of(), null, seatPolicy(), club.isAllowNudge(), nextMeetingAt(clubId),
                club.getBackgroundUrl());
    }

    private ClubSeatPolicy seatPolicy() {
        BookeyProperties.Club policy = properties.club();
        return new ClubSeatPolicy(policy.freeMemberLimit(), policy.maxMemberLimit(), policy.seatCostBookmarks(), policy.seatStep());
    }

    private MemberProgressView toMemberView(ClubMember member, User user, ReadingRecord record,
                                            Book book, Long viewerId, boolean clubAllowsNudge) {
        boolean isMe = member.getUserId().equals(viewerId);
        boolean share = member.isShareProgress() || isMe;

        if (!share || record == null) {
            return new MemberProgressView(
                    member.getUserId(), member.getId(),
                    user == null ? "알 수 없음" : user.getNickname(),
                    user == null ? null : user.getAvatarUrl(),
                    member.getRole(), isMe, member.isShareProgress(),
                    null, null, null, null, null, null,
                    false);
        }
        Double rate = completionRate(record, book);
        long duration = sessionRepository.sumDurationSec(record.getId());
        boolean finished = record.getStatus() == ReadingStatus.FINISHED;
        String paceStatus = paceStatus(record, book);
        boolean nudgeable = clubAllowsNudge && member.isAllowNudge() && !isMe && !finished;

        return new MemberProgressView(
                member.getUserId(), member.getId(),
                user == null ? "알 수 없음" : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                member.getRole(), isMe, true,
                record.getCurrentPage(), rate, duration,
                member.getLastReadAt() == null ? record.getLastReadAt() : member.getLastReadAt(),
                finished, paceStatus, nudgeable);
    }

    private String paceStatus(ReadingRecord record, Book book) {
        var progress = progressService.calculate(record, book);
        return switch (progress.lagLevel()) {
            case L0_NORMAL -> "ON_TRACK";
            case L1_CAUTION, L2_DELAYED -> "BEHIND";
            default -> "AT_RISK";
        };
    }

    private Double completionRate(ReadingRecord record, Book book) {
        if (record == null) {
            return null;
        }
        int total = record.effectiveTotalPages(book == null ? null : book.getTotalPages());
        if (total <= 0) {
            return null;
        }
        return Math.min(1.0, (double) record.getCurrentPage() / total);
    }

    private Double averageCompletion(List<ClubMember> members, Map<Long, ReadingRecord> records,
                                     Book book) {
        List<Double> rates = members.stream()
                .filter(ClubMember::isShareProgress)
                .map(m -> completionRate(records.get(m.getReadingRecordId()), book))
                .filter(Objects::nonNull)
                .toList();
        if (rates.isEmpty()) {
            return null;
        }
        return rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /**
     * 멤버의 지금 책 기록 — 읽을 책이 아직 없는 모임은 기록이 null 인 멤버가 있다.
     * 호출자가 null 키로도 조회하므로 null 조회를 허용하는 HashMap 으로 돌려준다(Map.of() 는 NPE).
     */
    private Map<Long, ReadingRecord> loadRecords(List<ClubMember> members) {
        List<Long> ids = members.stream()
                .map(ClubMember::getReadingRecordId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return recordRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(ReadingRecord::getId, Function.identity()));
    }

    /** 목록 카드용 멤버 요약 — 진척 높은 순, 비공개 멤버는 진척 null 에 맨 뒤. */
    private List<ClubMemberBrief> memberBriefs(List<ClubMember> peers, Map<Long, ReadingRecord> records,
                                               Book book, Long viewerId) {
        Map<Long, User> users = loadUsers(peers);
        Set<Long> liveRecordIds = openSessionRecordIds(peers);
        return peers.stream()
                .map(p -> {
                    User user = users.get(p.getUserId());
                    boolean isMe = p.getUserId().equals(viewerId);
                    boolean share = p.isShareProgress() || isMe;
                    return new ClubMemberBrief(
                            p.getUserId(),
                            user == null ? "알 수 없음" : user.getNickname(),
                            user == null ? null : user.getAvatarUrl(),
                            p.getRole(), isMe,
                            share ? completionRate(records.get(p.getReadingRecordId()), book) : null,
                            share && p.getReadingRecordId() != null && liveRecordIds.contains(p.getReadingRecordId()));
                })
                .sorted(Comparator.comparing(
                        (ClubMemberBrief b) -> b.completionRate() == null ? -1.0 : b.completionRate())
                        .reversed())
                .toList();
    }

    /** 열린 읽기 세션이 있는 기록 id — ClubLogService.readingNow 와 같은 규칙(쉬는 세션·4시간 넘은 세션은 제외). */
    private Set<Long> openSessionRecordIds(List<ClubMember> members) {
        List<Long> recordIds = members.stream()
                .map(ClubMember::getReadingRecordId).filter(Objects::nonNull).distinct().toList();
        if (recordIds.isEmpty()) {
            return Set.of();
        }
        Instant now = Instant.now();
        return sessionRepository.findAllByReadingRecordIdInAndEndedAtIsNull(recordIds).stream()
                .filter(s -> s.isReadingNow(now))
                .map(ReadingSession::getReadingRecordId)
                .collect(Collectors.toSet());
    }

    private Map<Long, User> loadUsers(List<ClubMember> members) {
        List<Long> ids = members.stream().map(ClubMember::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    // ────────────────────────────── 결산 ──────────────────────────────

    @Transactional(readOnly = true)
    public ClubResultView result(Long userId, Long clubId) {
        Club club = getClub(clubId);
        memberOrThrow(clubId, userId);
        Book book = currentBook(club);

        List<ClubMember> members =
                memberRepository.findAllByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);
        Map<Long, ReadingRecord> records = loadRecords(members);
        Map<Long, User> users = loadUsers(members);

        long finished = records.values().stream()
                .filter(r -> r.getStatus() == ReadingStatus.FINISHED)
                .count();
        long totalDuration = records.values().stream()
                .mapToLong(r -> sessionRepository.sumDurationSec(r.getId()))
                .sum();

        List<MemberProgressView> memberViews = members.stream()
                .map(m -> toMemberView(m, users.get(m.getUserId()), records.get(m.getReadingRecordId()),
                        book, userId, false))
                .sorted(Comparator.comparing(
                        (MemberProgressView v) -> v.completionRate() == null ? -1.0 : v.completionRate())
                        .reversed())
                .toList();

        List<String> bestQuotes = postRepository
                .findBestQuotes(clubId, PageRequest.of(0, 3)).stream()
                .map(ClubPost::getBody)
                .toList();

        // 토론왕 — 한 글도 안 쓴 모임에서는 아무도 뽑지 않는다.
        String topDiscussant = members.stream()
                .map(m -> Map.entry(m, postRepository.countDiscussions(clubId, m.getUserId())))
                .filter(e -> e.getValue() > 0)
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(e -> users.get(e.getKey().getUserId()))
                .map(User::getNickname)
                .orElse(null);

        return new ClubResultView(
                club.getId(), club.getName(), book == null ? null : BookSummary.from(book),
                members.size(), finished,
                members.isEmpty() ? 0 : (double) finished / members.size(),
                totalDuration, memberViews, bestQuotes, topDiscussant);
    }

    // ────────────────────────────── 공통 ──────────────────────────────

    @Transactional(readOnly = true)
    public Club getClub(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ClubMember activeMember(Long clubId, Long userId) {
        return memberRepository.findByClubIdAndUserId(clubId, userId)
                .filter(ClubMember::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_MEMBER));
    }

    private ClubMember memberOrThrow(Long clubId, Long userId) {
        return memberRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_MEMBER));
    }

    private void requireHost(Club club, Long userId) {
        if (!club.isHost(userId)) {
            throw ApiException.of(ErrorCode.CLUB_NOT_HOST);
        }
    }

    private void requireOpsEnabled(String key, String message) {
        opsFlagRepository.findById(key).ifPresent(flag -> {
            if (!flag.isEnabled()) {
                throw new ApiException(ErrorCode.FORBIDDEN, message);
            }
        });
    }
}
