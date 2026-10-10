package app.bookey.admin;

import app.bookey.admin.dto.AdminCatalogDtos.*;
import app.bookey.admin.dto.AdminDtos.BookRow;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.api.club.ClubService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookPageSuggestionRepository;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.book.BookSource;
import app.bookey.domain.club.Club;
import app.bookey.domain.club.ClubBookRepository;
import app.bookey.domain.club.ClubMember;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubMemberStatus;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 도서·모임 관리 — 도서 등록·사용처·페이지 수 제안 검토·병합, 모임 상세·멤버·강퇴.
 * 여러 테이블을 한 번에 세거나 옮기는 일은 JdbcTemplate 로 한다(AccountDeletionJob 선례).
 */
@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BookRepository bookRepository;
    private final BookPageSuggestionRepository suggestionRepository;
    private final ClubRepository clubRepository;
    private final ClubBookRepository clubBookRepository;
    private final ClubMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ClubService clubService;
    private final AdminAuditService auditService;
    private final JdbcTemplate jdbc;

    // ── 도서 ────────────────────────────────────────────────

    /** 관리자가 직접 등록한 책 — 외부 검색에 없는 책. ISBN 이 이미 있으면 그 책을 알려 준다(409). */
    @Transactional
    public BookRow createBook(AuthAdmin admin, AdminBookCreateRequest req) {
        if (!admin.role().canEditBook()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        String isbn = normalizeIsbn(req.isbn13());
        if (isbn != null) {
            bookRepository.findByIsbn13(isbn).ifPresent(existing -> {
                throw new ApiException(ErrorCode.CONFLICT,
                        "이미 등록된 ISBN 입니다 — 도서 #" + existing.getId() + " " + existing.getTitle());
            });
        }
        Book book = bookRepository.save(Book.builder()
                .isbn13(isbn)
                .title(req.title().trim())
                .author(blankToNull(req.author()))
                .publisher(blankToNull(req.publisher()))
                .totalPages(req.totalPages())
                .coverUrl(blankToNull(req.coverUrl()))
                .category(blankToNull(req.category()))
                .source(BookSource.MANUAL)
                .userCreated(false)
                .build());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("isbn13", isbn);
        after.put("title", book.getTitle());
        after.put("totalPages", book.getTotalPages());
        auditService.log(admin, "CREATE_BOOK", "BOOK", book.getId(), req.reason(), null, after);
        return toRow(book);
    }

    @Transactional(readOnly = true)
    public AdminBookView book(Long bookId) {
        Book book = bookRepository.findById(bookId).orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        return toView(book);
    }

    public static BookRow toRow(Book book) {
        return new BookRow(book.getId(), book.getIsbn13(), book.getTitle(), book.getAuthor(), book.getPublisher(),
                book.getTotalPages(), book.getSource().name(), book.isUserCreated(), book.getCreatedAt(),
                book.getCoverUrl(), book.getCategory());
    }

    private AdminBookView toView(Book book) {
        return new AdminBookView(book.getId(), book.getIsbn13(), book.getTitle(), book.getAuthor(), book.getPublisher(),
                book.getTotalPages(), book.getCoverUrl(), book.getCategory(), book.getSource().name(),
                book.isUserCreated(), book.getCreatedAt(), usage(book.getId()));
    }

    /** 이 책을 가리키는 행 수 — 책을 참조하는 테이블 전부. 새 참조가 생기면 여기와 merge 에 함께 더한다. */
    BookUsage usage(Long bookId) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM reading_records WHERE book_id = ?),
                       (SELECT COUNT(*) FROM reviews WHERE book_id = ?),
                       (SELECT COUNT(*) FROM posts WHERE book_id = ?),
                       (SELECT COUNT(*) FROM book_remarks WHERE book_id = ?),
                       (SELECT COUNT(*) FROM book_likes WHERE book_id = ?),
                       (SELECT COUNT(*) FROM club_books WHERE book_id = ?),
                       (SELECT COUNT(*) FROM club_meetings WHERE book_id = ?),
                       (SELECT COUNT(*) FROM book_page_suggestions WHERE book_id = ?),
                       (SELECT COUNT(*) FROM share_cards WHERE book_id = ?),
                       EXISTS (SELECT 1 FROM editor_picks WHERE book_id = ?)
                """, (rs, i) -> new BookUsage(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getBoolean(10)),
                bookId, bookId, bookId, bookId, bookId, bookId, bookId, bookId, bookId, bookId);
    }

    // ── 페이지 수 제안 ──────────────────────────────────────

    /**
     * 제안이 모인 책 — 가장 최근 제안 순. onlyConflicts 면 지금 페이지 수가 없거나 최다 득표와 다른 책만.
     * (제안은 책에 페이지 수가 없을 때만 3표로 자동 채택된다 — 이미 값이 있으면 사람이 봐야 한다.)
     */
    @Transactional(readOnly = true)
    public PageResponse<PageSuggestionRow> pageSuggestions(boolean onlyConflicts, int page, int size) {
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        String base = """
                WITH tally AS (
                    SELECT book_id, total_pages, COUNT(*) AS votes, MAX(created_at) AS last_at
                    FROM book_page_suggestions GROUP BY book_id, total_pages),
                ranked AS (
                    SELECT book_id, total_pages, votes,
                           ROW_NUMBER() OVER (PARTITION BY book_id ORDER BY votes DESC, total_pages) AS rn
                    FROM tally),
                agg AS (SELECT book_id, SUM(votes) AS total_votes, MAX(last_at) AS last_at FROM tally GROUP BY book_id)
                SELECT %s
                FROM agg a
                JOIN books b ON b.id = a.book_id
                JOIN ranked r ON r.book_id = a.book_id AND r.rn = 1
                WHERE (? = FALSE OR b.total_pages IS NULL OR b.total_pages <> r.total_pages)
                """;
        long total = jdbc.queryForObject(base.formatted("COUNT(*)"), Long.class, onlyConflicts);
        List<PageSuggestionRow> rows = jdbc.query(
                base.formatted("b.id, b.title, b.total_pages, r.total_pages, r.votes, a.total_votes, a.last_at")
                        + " ORDER BY a.last_at DESC LIMIT ? OFFSET ?",
                (rs, i) -> new PageSuggestionRow(rs.getLong(1), rs.getString(2),
                        (Integer) rs.getObject(3), rs.getInt(4), rs.getLong(5), rs.getLong(6),
                        rs.getTimestamp(7).toInstant()),
                onlyConflicts, limit, (long) page * limit);
        return PageResponse.of(new PageImpl<>(rows, PageRequest.of(page, limit), total));
    }

    @Transactional(readOnly = true)
    public List<PageSuggestionTally> tally(Long bookId) {
        bookRepository.findById(bookId).orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        return suggestionRepository.tallyVotes(bookId).stream()
                .map(row -> new PageSuggestionTally(((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    /** 제안을 비운다 — 판본이 달라 엇갈린 표가 쌓였을 때. 페이지 수 반영은 도서 수정(PATCH)으로 한다. */
    @Transactional
    public int clearSuggestions(AuthAdmin admin, Long bookId, String reason) {
        if (!admin.role().canEditBook()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        bookRepository.findById(bookId).orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        List<PageSuggestionTally> before = tally(bookId);
        int removed = jdbc.update("DELETE FROM book_page_suggestions WHERE book_id = ?", bookId);
        auditService.log(admin, "CLEAR_PAGE_SUGGESTIONS", "BOOK", bookId, reason,
                Map.of("tally", before.stream().map(t -> t.pages() + ":" + t.votes()).toList()),
                Map.of("removed", removed));
        return removed;
    }

    // ── 병합 ────────────────────────────────────────────────

    /**
     * 병합 미리보기. 원본(source)은 사람이 직접 등록했거나 ISBN 이 없는 책만 — 외부 검색으로 들어온 책을 지우면
     * 다음 검색 때 같은 ISBN 으로 다시 생긴다. 같은 모임에 두 책이 다 있으면 막는다(모임 책 순서가 겹친다).
     */
    @Transactional(readOnly = true)
    public BookMergePreview mergePreview(AuthAdmin admin, Long sourceId, Long targetId) {
        if (!admin.role().canMergeBooks()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        Book source = bookRepository.findById(sourceId).orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        Book target = bookRepository.findById(targetId).orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        List<String> blockers = new ArrayList<>();
        if (sourceId.equals(targetId)) {
            blockers.add("같은 책끼리는 합칠 수 없습니다.");
        }
        if (!source.isUserCreated() && source.getIsbn13() != null) {
            blockers.add("원본이 외부 검색으로 들어온 책(ISBN 있음)이라 지우면 다시 생깁니다. 사용자가 등록했거나 ISBN 이 없는 책만 원본으로 쓸 수 있습니다.");
        }
        long sharedClubs = count("""
                SELECT COUNT(*) FROM club_books s JOIN club_books t ON s.club_id = t.club_id
                WHERE s.book_id = ? AND t.book_id = ?""", sourceId, targetId);
        if (sharedClubs > 0) {
            blockers.add("두 책을 모두 고른 모임이 " + sharedClubs + "개 있습니다. 모임에서 한 권을 빼고 다시 시도하세요.");
        }
        List<String> warnings = new ArrayList<>();
        long doubleOpen = count("""
                SELECT COUNT(DISTINCT s.user_id) FROM reading_records s JOIN reading_records t ON s.user_id = t.user_id
                WHERE s.book_id = ? AND t.book_id = ?
                  AND s.status NOT IN ('FINISHED', 'ABANDONED') AND t.status NOT IN ('FINISHED', 'ABANDONED')""",
                sourceId, targetId);
        if (doubleOpen > 0) {
            warnings.add("두 책을 모두 서재에 담아 두고 아직 끝내지 않은 회원이 " + doubleOpen
                    + "명 있습니다. 합친 뒤 이 회원들 서재에는 같은 책이 두 번(회차로) 보이고, 하나는 회원이 직접 지울 수 있습니다.");
        }
        return new BookMergePreview(toView(source), toView(target),
                count("""
                        SELECT COUNT(DISTINCT s.user_id) FROM reading_records s JOIN reading_records t
                          ON s.user_id = t.user_id WHERE s.book_id = ? AND t.book_id = ?""", sourceId, targetId),
                count("""
                        SELECT COUNT(*) FROM book_likes s JOIN book_likes t ON s.user_id = t.user_id
                        WHERE s.book_id = ? AND t.book_id = ?""", sourceId, targetId),
                count("""
                        SELECT COUNT(*) FROM book_page_suggestions s JOIN book_page_suggestions t ON s.user_id = t.user_id
                        WHERE s.book_id = ? AND t.book_id = ?""", sourceId, targetId),
                blockers, warnings, blockers.isEmpty());
    }

    /**
     * 원본을 대상에 합친다 — 원본을 가리키던 모든 행을 대상으로 옮기고 원본을 지운다(한 트랜잭션).
     *  - 독서 기록: 두 책을 다 읽은 회원은 원본 쪽 회차를 대상 회차 뒤로 미룬다(회원·책·회차가 겹치면 안 된다).
     *  - 좋아요·페이지 수 제안·에디터 픽: 겹치면 원본 쪽을 지운다.
     */
    @Transactional
    public BookMergeResult merge(AuthAdmin admin, Long sourceId, BookMergeRequest req) {
        BookMergePreview preview = mergePreview(admin, sourceId, req.targetId());
        if (!preview.mergeable()) {
            throw new ApiException(ErrorCode.CONFLICT, String.join(" ", preview.blockers()));
        }
        long s = sourceId;
        long t = req.targetId();

        jdbc.update("""
                UPDATE reading_records r
                SET round = r.round + (SELECT COALESCE(MAX(x.round), 0) FROM reading_records x
                                       WHERE x.user_id = r.user_id AND x.book_id = ?)
                WHERE r.book_id = ?
                  AND EXISTS (SELECT 1 FROM reading_records x WHERE x.user_id = r.user_id AND x.book_id = ?)""", t, s, t);
        int records = jdbc.update("UPDATE reading_records SET book_id = ? WHERE book_id = ?", t, s);

        jdbc.update("""
                DELETE FROM book_likes WHERE book_id = ?
                  AND user_id IN (SELECT user_id FROM book_likes WHERE book_id = ?)""", s, t);
        int likes = jdbc.update("UPDATE book_likes SET book_id = ? WHERE book_id = ?", t, s);

        jdbc.update("""
                DELETE FROM book_page_suggestions WHERE book_id = ?
                  AND user_id IN (SELECT user_id FROM book_page_suggestions WHERE book_id = ?)""", s, t);
        int suggestions = jdbc.update("UPDATE book_page_suggestions SET book_id = ? WHERE book_id = ?", t, s);

        jdbc.update("DELETE FROM editor_picks WHERE book_id = ? AND EXISTS (SELECT 1 FROM editor_picks WHERE book_id = ?)", s, t);
        int picks = jdbc.update("UPDATE editor_picks SET book_id = ? WHERE book_id = ?", t, s);

        int clubBooks = jdbc.update("UPDATE club_books SET book_id = ? WHERE book_id = ?", t, s);
        int meetings = jdbc.update("UPDATE club_meetings SET book_id = ? WHERE book_id = ?", t, s);
        int reviews = jdbc.update("UPDATE reviews SET book_id = ? WHERE book_id = ?", t, s);
        int posts = jdbc.update("UPDATE posts SET book_id = ? WHERE book_id = ?", t, s);
        int remarks = jdbc.update("UPDATE book_remarks SET book_id = ? WHERE book_id = ?", t, s);
        int shareCards = jdbc.update("UPDATE share_cards SET book_id = ? WHERE book_id = ?", t, s);
        jdbc.update("DELETE FROM books WHERE id = ?", s);

        BookUsage moved = new BookUsage(records, reviews, posts, remarks, likes, clubBooks, meetings, suggestions,
                shareCards, picks > 0);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("sourceTitle", preview.source().title());
        before.put("sourceIsbn", preview.source().isbn13());
        before.put("targetId", t);
        before.put("targetTitle", preview.target().title());
        auditService.log(admin, "MERGE_BOOK", "BOOK", s, req.reason(), before, Map.of(
                "records", records, "reviews", reviews, "posts", posts, "remarks", remarks, "likes", likes,
                "clubBooks", clubBooks, "meetings", meetings, "suggestions", suggestions, "shareCards", shareCards));
        return new BookMergeResult(t, moved);
    }

    // ── 모임 ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminClubView club(Long clubId) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        List<Long> bookIds = clubBookRepository.findAllByClubIdOrderBySeqAsc(clubId).stream()
                .map(cb -> cb.getBookId()).toList();
        Map<Long, String> titles = bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Book::getTitle, (a, b) -> a));
        return new AdminClubView(club.getId(), club.getName(), club.getDescription(), club.getVisibility(),
                club.getStatus(), club.getJoinCode(), club.getMemberCount(), club.getMemberLimit(), club.getOwnerId(),
                userRepository.findById(club.getOwnerId()).map(User::getNickname).orElse(null),
                club.getStartsAt(), club.getEndsAt(), club.getCreatedAt(),
                count("SELECT COUNT(*) FROM club_posts WHERE club_id = ? AND status = 'VISIBLE'", clubId),
                count("SELECT COUNT(*) FROM club_meetings WHERE club_id = ?", clubId),
                bookIds.stream().map(id -> titles.getOrDefault(id, "#" + id)).toList());
    }

    /** 멤버 — 활성(호스트 먼저) 다음에 나간·내보내진 멤버. status 를 주면 그 상태만. */
    @Transactional(readOnly = true)
    public List<AdminClubMemberRow> members(Long clubId, ClubMemberStatus status) {
        clubRepository.findById(clubId).orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        List<ClubMember> found = status == null
                ? memberRepository.findAllByClubIdOrderByJoinedAtAsc(clubId)
                : memberRepository.findAllByClubIdAndStatus(clubId, status);
        List<ClubMember> members = found.stream()
                .sorted(Comparator.comparing((ClubMember m) -> m.getStatus() != ClubMemberStatus.ACTIVE)
                        .thenComparing(m -> m.getRole().ordinal())
                        .thenComparing(ClubMember::getJoinedAt))
                .toList();
        Map<Long, User> users = userRepository.findAllById(members.stream().map(ClubMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        return members.stream().map(m -> {
            User user = users.get(m.getUserId());
            return new AdminClubMemberRow(m.getUserId(), user == null ? null : user.getNickname(),
                    user == null ? null : user.getHandle(), user == null ? null : user.getStatus(),
                    m.getRole(), m.getStatus(), m.getJoinedAt(), m.getLeftAt(), m.getKickReason());
        }).toList();
    }

    @Transactional
    public void kick(AuthAdmin admin, Long clubId, Long userId, String reason) {
        if (!admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        clubService.adminKick(clubId, userId, reason.trim());
        auditService.log(admin, "KICK_CLUB_MEMBER", "CLUB", clubId, reason, null, Map.of("userId", userId));
    }

    // ── 내부 ────────────────────────────────────────────────

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** ISBN 은 숫자만 남겨 13자리일 때만 쓴다(하이픈·공백 허용). 비면 null. */
    static String normalizeIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        String digits = isbn.replaceAll("[^0-9]", "");
        if (digits.length() != 13) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "ISBN 은 13자리 숫자로 적어 주세요.");
        }
        return digits;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
