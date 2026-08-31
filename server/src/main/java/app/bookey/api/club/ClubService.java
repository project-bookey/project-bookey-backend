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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ClubCheckpointRepository checkpointRepository;
    private final ClubCheckpointProgressRepository checkpointProgressRepository;
    private final ClubPostRepository postRepository;
    private final ClubEventRepository eventRepository;
    private final BookRepository bookRepository;
    private final ReadingRecordRepository recordRepository;
    private final ReadingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final OpsFlagRepository opsFlagRepository;
    private final ProgressService progressService;
    private final RateLimiter rateLimiter;
    private final BookeyProperties properties;

    // ────────────────────────────── 생성 ──────────────────────────────

    @Transactional
    public ClubHomeView create(Long userId, CreateClubRequest request) {
        requireOpsEnabled(OpsFlag.CLUB_CREATION_OPEN, "현재 모임 생성이 중단되었습니다.");
        rateLimiter.require("club:create:" + userId, CLUB_CREATE_DAILY_LIMIT, Duration.ofDays(1));

        if (request.endsAt().isBefore(request.startsAt())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "종료일이 시작일보다 빠릅니다.");
        }
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));

        short memberLimit = request.memberLimit() == null
                ? (short) properties.club().defaultMemberLimit()
                : request.memberLimit().shortValue();

        Club club = clubRepository.save(Club.builder()
                .ownerId(userId)
                .name(request.name())
                .description(request.description())
                .coverUrl(book.getCoverUrl())
                .joinCode(generateUniqueCode())
                .visibility(request.visibility())
                .memberLimit(memberLimit)
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .allowNudge(request.allowNudge() == null || request.allowNudge())
                .build());

        ClubBook clubBook = clubBookRepository.save(ClubBook.builder()
                .clubId(club.getId())
                .bookId(book.getId())
                .seq((short) 1)
                .targetFinishDate(request.endsAt())
                .totalPagesSnapshot(book.getTotalPages())
                .build());

        createCheckpoints(clubBook, book, request);

        // 호스트도 멤버로 참가한다.
        ReadingRecord record = ensureReadingRecord(userId, book, request.endsAt(), true);
        Club saved = club;
        memberRepository.save(ClubMember.builder()
                .clubId(saved.getId())
                .userId(userId)
                .readingRecordId(record.getId())
                .role(ClubRole.HOST)
                .shareProgress(true)
                .allowNudge(true)
                .build());
        club.joinMember();

        eventRepository.save(new ClubEvent(club.getId(), userId, ClubEventType.CREATED,
                Map.of("bookId", book.getId(), "name", club.getName())));

        return home(userId, club.getId());
    }

    private void createCheckpoints(ClubBook clubBook, Book book, CreateClubRequest request) {
        if (request.checkpoints() != null && !request.checkpoints().isEmpty()) {
            short seq = 1;
            for (CheckpointRequest cp : request.checkpoints()) {
                checkpointRepository.save(ClubCheckpoint.builder()
                        .clubBookId(clubBook.getId())
                        .seq(seq)
                        .title(cp.title() == null || cp.title().isBlank() ? seq + "주차" : cp.title())
                        .targetPage(cp.targetPage())
                        .dueAt(cp.dueAt())
                        .build());
                seq++;
            }
            return;
        }
        if (!Boolean.TRUE.equals(request.autoCheckpoints()) || book.getTotalPages() == null) {
            return;
        }
        // 총 페이지를 주차 수로 균등 분배 (§12.2 자동 생성 옵션)
        long totalDays = ChronoUnit.DAYS.between(request.startsAt(), request.endsAt());
        int weeks = (int) Math.max(1, Math.ceil(totalDays / 7.0));
        int totalPages = book.getTotalPages();
        for (int i = 1; i <= weeks; i++) {
            int targetPage = (int) Math.round((double) totalPages * i / weeks);
            LocalDate due = request.startsAt().plusWeeks(i);
            if (due.isAfter(request.endsAt())) {
                due = request.endsAt();
            }
            checkpointRepository.save(ClubCheckpoint.builder()
                    .clubBookId(clubBook.getId())
                    .seq((short) i)
                    .title(i + "주차")
                    .targetPage(Math.max(1, targetPage))
                    .dueAt(due.atTime(23, 59).atZone(KST).toInstant())
                    .build());
        }
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
        ClubBook clubBook = clubBookRepository.findFirstByClubIdOrderBySeqAsc(club.getId())
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        Book book = bookRepository.findById(clubBook.getBookId()).orElse(null);
        User host = userRepository.findById(club.getOwnerId()).orElse(null);

        Optional<ClubMember> membership = memberRepository.findByClubIdAndUserId(club.getId(), userId);
        boolean alreadyMember = membership.map(ClubMember::isActive).orElse(false);

        String blockedReason = null;
        if (membership.map(m -> m.getStatus() == ClubMemberStatus.KICKED).orElse(false)) {
            blockedReason = "다시 참가할 수 없는 모임입니다.";
        } else if (club.getStatus().isOver()) {
            blockedReason = "이미 종료된 모임입니다.";
        } else if (club.isFull()) {
            blockedReason = "정원이 가득 찼습니다.";
        }

        return new ClubPreview(
                club.getId(), club.getName(), club.getDescription(),
                book == null ? null : BookSummary.from(book),
                host == null ? null : host.getNickname(),
                club.getMemberCount(), club.getMemberLimit(),
                club.getStartsAt(), club.getEndsAt(), club.getStatus(),
                alreadyMember, blockedReason == null && !alreadyMember, blockedReason);
    }

    @Transactional
    public ClubHomeView join(Long userId, JoinRequest request) {
        String code = JoinCodeGenerator.normalize(request.code());
        Club club = clubRepository.findByJoinCode(code)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_CODE_INVALID));

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

        ClubBook clubBook = clubBookRepository.findFirstByClubIdOrderBySeqAsc(club.getId())
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        Book book = bookRepository.findById(clubBook.getBookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));

        boolean adoptTarget = request.adoptTargetDate() == null || request.adoptTargetDate();
        ReadingRecord record = ensureReadingRecord(userId, book, club.getEndsAt(), adoptTarget);
        boolean shareProgress = request.shareProgress() == null || request.shareProgress();

        club.joinMember();   // 정원·종료 검사 포함
        existing.ifPresentOrElse(
                member -> {
                    member.rejoin(record.getId());
                    member.updateSharing(shareProgress, true);
                },
                () -> memberRepository.save(ClubMember.builder()
                        .clubId(club.getId())
                        .userId(userId)
                        .readingRecordId(record.getId())
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
            throw new ApiException(ErrorCode.INVALID_REQUEST, "자신을 강퇴할 수 없습니다.");
        }
        ClubMember target = activeMember(clubId, request.userId());
        target.kick(request.reason());
        club.leaveMember();
        eventRepository.save(new ClubEvent(clubId, request.userId(), ClubEventType.KICKED,
                Map.of("reason", request.reason())));
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
        club.update(request.name(), request.description(), request.visibility(),
                request.memberLimit(), request.endsAt(), request.allowNudge());
        return home(userId, clubId);
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
        Map<Long, ClubBook> clubBooks = clubBookRepository.findAllByClubIdIn(clubIds).stream()
                .collect(Collectors.toMap(ClubBook::getClubId, Function.identity(), (a, b) -> a));
        Map<Long, Book> books = bookRepository
                .findAllById(clubBooks.values().stream().map(ClubBook::getBookId).toList()).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        LocalDate today = LocalDate.now(KST);
        return PageResponse.of(memberships, membership -> {
            Club club = clubs.get(membership.getClubId());
            ClubBook clubBook = clubBooks.get(club.getId());
            Book book = clubBook == null ? null : books.get(clubBook.getBookId());
            List<ClubMember> peers = memberRepository
                    .findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE);
            Map<Long, ReadingRecord> records = loadRecords(peers);

            Double mine = completionRate(records.get(membership.getReadingRecordId()), book);
            Double average = averageCompletion(peers, records, book);

            return new ClubSummaryView(
                    club.getId(), club.getName(), club.getCoverUrl(),
                    book == null ? null : BookSummary.from(book),
                    club.getStatus(), club.getMemberCount(), club.daysLeft(today),
                    mine, average, 0);
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

        ClubBook clubBook = clubBookRepository.findFirstByClubIdOrderBySeqAsc(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        Book book = bookRepository.findById(clubBook.getBookId()).orElse(null);

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

        List<CheckpointView> checkpoints = checkpointViews(clubBook.getId(), me, members.size());
        CheckpointView next = checkpoints.stream()
                .filter(c -> !c.evaluated())
                .findFirst()
                .orElse(null);

        return new ClubHomeView(
                club.getId(), club.getName(), club.getDescription(), club.getCoverUrl(),
                club.getJoinCode(), club.getVisibility(), club.getStatus(),
                book == null ? null : BookSummary.from(book),
                club.getStartsAt(), club.getEndsAt(), club.daysLeft(LocalDate.now(KST)),
                club.getMemberCount(), club.getMemberLimit(),
                me.getRole(), me.isShareProgress(), me.isAllowNudge(),
                myRank, averageCompletion(members, records, book),
                memberViews, checkpoints, next);
    }

    private List<CheckpointView> checkpointViews(Long clubBookId, ClubMember me, int memberCount) {
        List<ClubCheckpoint> checkpoints =
                checkpointRepository.findAllByClubBookIdOrderBySeqAsc(clubBookId);
        if (checkpoints.isEmpty()) {
            return List.of();
        }
        List<Long> ids = checkpoints.stream().map(ClubCheckpoint::getId).toList();
        Map<Long, List<ClubCheckpointProgress>> progressByCheckpoint =
                checkpointProgressRepository.findAllByCheckpointIdIn(ids).stream()
                        .collect(Collectors.groupingBy(ClubCheckpointProgress::getCheckpointId));

        return checkpoints.stream().map(cp -> {
            List<ClubCheckpointProgress> progresses =
                    progressByCheckpoint.getOrDefault(cp.getId(), List.of());
            long achieved = progresses.stream().filter(ClubCheckpointProgress::isAchieved).count();
            Boolean myAchieved = progresses.stream()
                    .filter(p -> p.getClubMemberId().equals(me.getId()))
                    .map(ClubCheckpointProgress::isAchieved)
                    .findFirst()
                    .orElse(null);
            return new CheckpointView(cp.getId(), cp.getSeq(), cp.getTitle(), cp.getTargetPage(),
                    cp.getDueAt(), cp.getEvaluatedAt() != null, achieved, memberCount, myAchieved);
        }).toList();
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

    private Map<Long, ReadingRecord> loadRecords(List<ClubMember> members) {
        List<Long> ids = members.stream()
                .map(ClubMember::getReadingRecordId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return recordRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(ReadingRecord::getId, Function.identity()));
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

        ClubBook clubBook = clubBookRepository.findFirstByClubIdOrderBySeqAsc(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        Book book = bookRepository.findById(clubBook.getBookId()).orElse(null);

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

        String topDiscussant = members.stream()
                .max(Comparator.comparingLong(m ->
                        postRepository.countByClubIdAndUserIdAndStatus(clubId, m.getUserId(), "VISIBLE")))
                .map(m -> users.get(m.getUserId()))
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
