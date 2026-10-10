package app.bookey.admin;

import app.bookey.admin.dto.AdminContentDtos.AdminContentDetail;
import app.bookey.admin.dto.AdminContentDtos.AdminContentRow;
import app.bookey.admin.dto.AdminContentDtos.ContentAction;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.ModerationResolution;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.admin.ModerationStatus;
import app.bookey.domain.admin.ModerationTicket;
import app.bookey.domain.admin.ModerationTicketRepository;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.Club;
import app.bookey.domain.club.ClubPost;
import app.bookey.domain.club.ClubPostRepository;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostComment;
import app.bookey.domain.post.PostCommentRepository;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.remark.BookRemark;
import app.bookey.domain.remark.BookRemarkRepository;
import app.bookey.domain.report.AbuseReport;
import app.bookey.domain.report.AbuseReportRepository;
import app.bookey.domain.review.Review;
import app.bookey.domain.review.ReviewComment;
import app.bookey.domain.review.ReviewCommentRepository;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 콘텐츠 검수 — 독후감·리뷰·모임 글·독후감 댓글·리뷰 댓글·한줄평을 한 가지 모양으로 찾고, 보고, 조치한다.
 *  - 독후감·리뷰·모임 글: 숨김(작성자만 봄)·복구·삭제(행은 남기고 아무도 못 봄)
 *  - 댓글·한줄평: 상태가 없어 삭제(완전 삭제)만. 지운 원문은 감사 로그에 남긴다.
 * 조치하면 그 콘텐츠의 열린 신고 티켓과 신고도 함께 처리한다. 신고 큐의 판정도 이 서비스로 콘텐츠에 반영한다.
 */
@Service
@RequiredArgsConstructor
public class AdminContentService {

    /** 콘텐츠 검수에서 다루는 종류 — 모임(CLUB)·회원(USER) 신고는 콘텐츠가 아니라 여기서 빠진다. */
    public static final Set<ModerationSource> CONTENT_TYPES = EnumSet.of(
            ModerationSource.POST, ModerationSource.REVIEW, ModerationSource.CLUB_POST,
            ModerationSource.POST_COMMENT, ModerationSource.REVIEW_COMMENT, ModerationSource.BOOK_REMARK);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_LENGTH = 200;
    /** 완전 삭제하는 댓글·한줄평의 원문을 감사 로그에 남길 때 최대 길이. */
    private static final int AUDIT_TEXT_LENGTH = 2000;

    private final PostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final ClubPostRepository clubPostRepository;
    private final PostCommentRepository postCommentRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final BookRemarkRepository remarkRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ClubRepository clubRepository;
    private final AbuseReportRepository reportRepository;
    private final ModerationTicketRepository ticketRepository;
    private final AdminAuditService auditService;

    /** 목록 필터 — null 이면 그 조건은 걸지 않는다. */
    public record Filter(Long userId, Long bookId, Long clubId, String status, String keyword, boolean reportedOnly) {}

    /** 종류와 무관하게 콘텐츠 한 건을 다루는 공통 모양. */
    record Snap(ModerationSource type, Long id, Long authorId, String title, String body, String status,
                String visibility, Long bookId, Long clubId, Long parentId, String imageUrl, Instant createdAt) {

        /** 숨김·복구가 되는(상태 열이 있는) 종류. */
        boolean statusful() {
            return type == ModerationSource.POST || type == ModerationSource.REVIEW
                    || type == ModerationSource.CLUB_POST;
        }

        List<ContentAction> actions() {
            if (!statusful()) {
                return List.of(ContentAction.DELETE);
            }
            return switch (status) {
                case "VISIBLE" -> List.of(ContentAction.HIDE, ContentAction.DELETE);
                case "HIDDEN" -> List.of(ContentAction.RESTORE, ContentAction.DELETE);
                default -> List.of(ContentAction.RESTORE);
            };
        }

        boolean privatePost() {
            return type == ModerationSource.POST && PostVisibility.PRIVATE.name().equals(visibility);
        }
    }

    // ── 목록 ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<AdminContentRow> list(AuthAdmin admin, ModerationSource type, Filter filter, int page, int size) {
        requireContentType(type);
        Filter f = normalize(filter);
        Pageable pageable = PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "id"));
        Page<Snap> snaps = switch (type) {
            case POST -> postRepository.findAll(
                    spec(type, f, List.of("title", "bodyMd"), true, true, true), pageable).map(this::snap);
            case REVIEW -> reviewRepository.findAll(
                    spec(type, f, List.of("body"), true, true, false), pageable).map(this::snap);
            case CLUB_POST -> clubPostRepository.findAll(
                    spec(type, f, List.of("body"), true, false, true), pageable).map(this::snap);
            case POST_COMMENT -> postCommentRepository.findAll(
                    spec(type, f, List.of("body"), false, false, false), pageable).map(this::snap);
            case REVIEW_COMMENT -> reviewCommentRepository.findAll(
                    spec(type, f, List.of("body"), false, false, false), pageable).map(this::snap);
            case BOOK_REMARK -> remarkRepository.findAll(
                    spec(type, f, List.of("body"), false, true, false), pageable).map(this::snap);
            default -> throw ApiException.of(ErrorCode.INVALID_REQUEST);
        };
        List<AdminContentRow> rows = toRows(admin, snaps.getContent());
        return PageResponse.of(new PageImpl<>(rows, pageable, snaps.getTotalElements()));
    }

    // ── 상세 ────────────────────────────────────────────────

    /** 원문 보기 — 열람 기록(VIEW_CONTENT)이 남는다. 비공개 독후감은 신고 처리 권한이 있어야 본다. */
    @Transactional
    public AdminContentDetail detail(AuthAdmin admin, ModerationSource type, Long id) {
        requireContentType(type);
        Snap snap = findSnap(type, id).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (snap.privatePost() && !admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        auditService.log(admin, "VIEW_CONTENT", type.name(), id, null, null, null);
        return toDetail(admin, snap);
    }

    /** 신고 큐 상세에 싣는 원문 — 열람 기록은 신고 상세 쪽이 남긴다. 없거나 볼 권한이 없으면 null. */
    @Transactional(readOnly = true)
    public AdminContentDetail detailForTicket(AuthAdmin admin, ModerationSource type, Long id) {
        if (!CONTENT_TYPES.contains(type)) {
            return null;
        }
        return findSnap(type, id)
                .filter(snap -> !snap.privatePost() || admin.canModerate())
                .map(snap -> toDetail(admin, snap))
                .orElse(null);
    }

    private AdminContentDetail toDetail(AuthAdmin admin, Snap snap) {
        Long ticketId = ticketRepository.findBySourceTypeAndSourceId(snap.type(), snap.id())
                .map(ModerationTicket::getId).orElse(null);
        AdminContentRow row = toRows(admin, List.of(snap)).getFirst();
        String body = snap.title() == null ? nullToEmpty(snap.body()) : snap.title() + "\n\n" + nullToEmpty(snap.body());
        return new AdminContentDetail(row, body, snap.imageUrl(), ticketId);
    }

    // ── 조치 ────────────────────────────────────────────────

    /**
     * 숨김·복구·삭제. 그 콘텐츠의 열린 신고 티켓도 같은 판정으로 닫는다(숨김→HIDE, 복구→KEEP, 삭제→DELETE).
     */
    @Transactional
    public void act(AuthAdmin admin, ModerationSource type, Long id, ContentAction action, String reason) {
        if (!admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        requireContentType(type);
        Snap snap = findSnap(type, id).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!snap.actions().contains(action)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이 콘텐츠에는 할 수 없는 조치입니다.");
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", snap.status());
        before.put("authorId", snap.authorId());
        // 완전 삭제는 되돌릴 수 없으니 원문을 남긴다. 나머지는 미리보기만.
        before.put("text", snap.statusful() ? truncate(snap.body(), PREVIEW_LENGTH) : truncate(snap.body(), AUDIT_TEXT_LENGTH));

        apply(snap, action);

        ticketRepository.findBySourceTypeAndSourceId(type, id)
                .filter(ticket -> ticket.getStatus() != ModerationStatus.RESOLVED)
                .ifPresent(ticket -> {
                    ticket.resolve(admin.id(), resolutionOf(action), reason);
                    reportRepository.resolveAllForTarget(type.name(), id);
                });

        auditService.log(admin, action.name() + "_CONTENT", type.name(), id, reason, before,
                Map.of("status", statusAfter(snap, action)));
    }

    /**
     * 신고 큐 판정을 대상에 반영한다. 댓글·한줄평은 숨김이 없어 숨김·제재 판정이면 지운다.
     * 모임은 삭제 판정이면 강제 종료한다. @return 제재를 걸 작성자(모임은 호스트, 회원 신고는 그 회원). 없으면 null.
     */
    @Transactional
    public Long applyResolution(ModerationSource type, Long id, ModerationResolution resolution) {
        if (type == ModerationSource.USER) {
            return userRepository.existsById(id) ? id : null;
        }
        if (type == ModerationSource.CLUB) {
            return clubRepository.findById(id).map(club -> {
                if (resolution == ModerationResolution.DELETE) {
                    club.end();
                }
                return club.getOwnerId();
            }).orElse(null);
        }
        Snap snap = findSnap(type, id).orElse(null);
        if (snap == null) {
            return null;
        }
        ContentAction action = switch (resolution) {
            case KEEP -> snap.statusful() ? ContentAction.RESTORE : null;
            case HIDE, SANCTION -> snap.statusful() ? ContentAction.HIDE : ContentAction.DELETE;
            case DELETE -> ContentAction.DELETE;
        };
        if (action != null && snap.actions().contains(action)) {
            apply(snap, action);
        }
        return snap.authorId();
    }

    /** 신고 큐 목록 한 줄 — 미리보기와 작성자. 대상이 사라졌으면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<Summary> summarize(ModerationSource type, Long id) {
        if (type == ModerationSource.USER) {
            return userRepository.findById(id).map(u -> new Summary("회원 " + u.getNickname(), u.getId()));
        }
        if (type == ModerationSource.CLUB) {
            return clubRepository.findById(id).map(c -> new Summary("모임 " + c.getName(), c.getOwnerId()));
        }
        return findSnap(type, id).map(s -> new Summary(
                truncate(s.title() == null ? s.body() : s.title() + " — " + nullToEmpty(s.body()), PREVIEW_LENGTH),
                s.authorId()));
    }

    public record Summary(String preview, Long authorId) {}

    // ── 내부 ────────────────────────────────────────────────

    private void apply(Snap snap, ContentAction action) {
        Long id = snap.id();
        switch (snap.type()) {
            case POST -> postRepository.findById(id).ifPresent(post -> {
                switch (action) {
                    case HIDE -> post.hide();
                    case RESTORE -> post.restore();
                    case DELETE -> post.softDelete();
                }
            });
            case REVIEW -> reviewRepository.findById(id).ifPresent(review -> {
                switch (action) {
                    case HIDE -> review.hide();
                    case RESTORE -> review.restore();
                    case DELETE -> review.softDelete();
                }
            });
            case CLUB_POST -> clubPostRepository.findById(id).ifPresent(post -> {
                switch (action) {
                    case HIDE -> post.hide();
                    case RESTORE -> post.restore();
                    case DELETE -> {
                        post.softDelete();
                        // 답글을 지우면 부모의 답글 수도 맞춘다(작성자 삭제와 같은 규칙).
                        if (post.getParentId() != null) {
                            clubPostRepository.findById(post.getParentId()).ifPresent(ClubPost::decreaseComment);
                        }
                    }
                }
            });
            // 댓글은 답글이 DB 에서 함께 지워진다(parent_id ON DELETE CASCADE).
            case POST_COMMENT -> postCommentRepository.findById(id).ifPresent(postCommentRepository::delete);
            case REVIEW_COMMENT -> reviewCommentRepository.findById(id).ifPresent(reviewCommentRepository::delete);
            case BOOK_REMARK -> remarkRepository.findById(id).ifPresent(remarkRepository::delete);
            default -> throw ApiException.of(ErrorCode.INVALID_REQUEST);
        }
    }

    private Optional<Snap> findSnap(ModerationSource type, Long id) {
        return switch (type) {
            case POST -> postRepository.findById(id).map(this::snap);
            case REVIEW -> reviewRepository.findById(id).map(this::snap);
            case CLUB_POST -> clubPostRepository.findById(id).map(this::snap);
            case POST_COMMENT -> postCommentRepository.findById(id).map(this::snap);
            case REVIEW_COMMENT -> reviewCommentRepository.findById(id).map(this::snap);
            case BOOK_REMARK -> remarkRepository.findById(id).map(this::snap);
            default -> Optional.empty();
        };
    }

    private Snap snap(Post p) {
        return new Snap(ModerationSource.POST, p.getId(), p.getUserId(), p.getTitle(), p.getBodyMd(), p.getStatus(),
                p.getVisibility().name(), p.getBookId(), p.getClubId(), null, null, p.getCreatedAt());
    }

    private Snap snap(Review r) {
        return new Snap(ModerationSource.REVIEW, r.getId(), r.getUserId(), null, r.getBody(), r.getStatus(),
                null, r.getBookId(), null, null, null, r.getCreatedAt());
    }

    private Snap snap(ClubPost c) {
        return new Snap(ModerationSource.CLUB_POST, c.getId(), c.getUserId(), null, c.getBody(), c.getStatus(),
                null, null, c.getClubId(), c.getParentId(), c.getImageUrl(), c.getCreatedAt());
    }

    private Snap snap(PostComment c) {
        return new Snap(ModerationSource.POST_COMMENT, c.getId(), c.getUserId(), null, c.getBody(), Post.VISIBLE,
                null, null, null, c.getPostId(), null, c.getCreatedAt());
    }

    private Snap snap(ReviewComment c) {
        return new Snap(ModerationSource.REVIEW_COMMENT, c.getId(), c.getUserId(), null, c.getBody(), Post.VISIBLE,
                null, null, null, c.getReviewId(), null, c.getCreatedAt());
    }

    private Snap snap(BookRemark r) {
        return new Snap(ModerationSource.BOOK_REMARK, r.getId(), r.getUserId(), null, r.getBody(), Post.VISIBLE,
                null, r.getBookId(), null, null, null, r.getCreatedAt());
    }

    /** 목록 행 — 작성자·책·모임·부모 글·신고 수를 종류별로 한 번에 읽어 붙인다(행 수와 무관하게 쿼리 수 고정). */
    private List<AdminContentRow> toRows(AuthAdmin admin, List<Snap> snaps) {
        if (snaps.isEmpty()) {
            return List.of();
        }
        Map<Long, User> authors = byId(userRepository.findAllById(ids(snaps, Snap::authorId)), User::getId);

        // 리뷰 댓글은 리뷰의 책 제목으로 맥락을 보여 준다.
        Map<Long, Review> parentReviews = byId(reviewRepository.findAllById(idsOf(snaps,
                ModerationSource.REVIEW_COMMENT, Snap::parentId)), Review::getId);
        Map<Long, Post> parentPosts = byId(postRepository.findAllById(idsOf(snaps,
                ModerationSource.POST_COMMENT, Snap::parentId)), Post::getId);

        List<Long> bookIds = new ArrayList<>(ids(snaps, Snap::bookId));
        parentReviews.values().forEach(review -> bookIds.add(review.getBookId()));
        Map<Long, String> bookTitles = bookRepository.findAllById(bookIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Book::getId, Book::getTitle, (a, b) -> a));
        Map<Long, String> clubNames = clubRepository.findAllById(ids(snaps, Snap::clubId)).stream()
                .collect(Collectors.toMap(Club::getId, Club::getName, (a, b) -> a));

        Map<ModerationSource, Map<Long, Long>> reportCounts = new HashMap<>();
        snaps.stream().collect(Collectors.groupingBy(Snap::type)).forEach((type, group) -> {
            Map<Long, Long> counts = new HashMap<>();
            for (Object[] row : reportRepository.countByTargets(type.name(), group.stream().map(Snap::id).toList())) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            reportCounts.put(type, counts);
        });

        return snaps.stream().map(snap -> {
            User author = authors.get(snap.authorId());
            String context = switch (snap.type()) {
                case POST -> snap.bookId() != null ? bookTitles.get(snap.bookId()) : clubNames.get(snap.clubId());
                case REVIEW, BOOK_REMARK -> bookTitles.get(snap.bookId());
                case CLUB_POST -> clubNames.get(snap.clubId());
                case POST_COMMENT -> Optional.ofNullable(parentPosts.get(snap.parentId()))
                        .map(post -> "독후감: " + post.getTitle()).orElse(null);
                case REVIEW_COMMENT -> Optional.ofNullable(parentReviews.get(snap.parentId()))
                        .map(review -> "리뷰: " + bookTitles.getOrDefault(review.getBookId(), "#" + review.getBookId()))
                        .orElse(null);
                default -> null;
            };
            // 비공개 독후감은 신고 처리 권한이 없으면 내용을 가린다.
            String preview = snap.privatePost() && !admin.canModerate()
                    ? "(비공개 글)"
                    : truncate(snap.body(), PREVIEW_LENGTH);
            return new AdminContentRow(snap.type(), snap.id(), snap.authorId(),
                    author == null ? null : author.getNickname(), author == null ? null : author.getStatus(),
                    snap.title(), preview, snap.status(), snap.visibility(),
                    reportCounts.getOrDefault(snap.type(), Map.of()).getOrDefault(snap.id(), 0L),
                    context, snap.bookId(), snap.clubId(), snap.parentId(), snap.createdAt(),
                    admin.canModerate() ? snap.actions() : List.of());
        }).toList();
    }

    /**
     * 종류 공통 검색 조건. 널 조건은 바인딩 대신 조건을 빼는 것으로 표현한다(PostgreSQL bytea 추론 문제 회피).
     * reportedOnly 는 신고가 한 건이라도 있는 것만 — 신고 테이블을 EXISTS 로 본다.
     */
    private static <T> Specification<T> spec(ModerationSource type, Filter f, List<String> textFields,
                                             boolean hasStatus, boolean hasBook, boolean hasClub) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.userId() != null) predicates.add(cb.equal(root.get("userId"), f.userId()));
            if (hasBook && f.bookId() != null) predicates.add(cb.equal(root.get("bookId"), f.bookId()));
            if (hasClub && f.clubId() != null) predicates.add(cb.equal(root.get("clubId"), f.clubId()));
            if (hasStatus && f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.keyword() != null) {
                String like = "%" + f.keyword().toLowerCase() + "%";
                predicates.add(cb.or(textFields.stream()
                        .map(field -> cb.like(cb.lower(root.<String>get(field)), like))
                        .toArray(Predicate[]::new)));
            }
            if (f.reportedOnly()) {
                Subquery<Long> reported = query.subquery(Long.class);
                Root<AbuseReport> report = reported.from(AbuseReport.class);
                reported.select(report.get("id")).where(
                        cb.equal(report.get("targetType"), type.name()),
                        cb.equal(report.get("targetId"), root.get("id")));
                predicates.add(cb.exists(reported));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Filter normalize(Filter filter) {
        if (filter == null) {
            return new Filter(null, null, null, null, null, false);
        }
        String keyword = filter.keyword() == null ? null : filter.keyword().replace("%", "").replace("_", "").trim();
        String status = filter.status() == null || filter.status().isBlank() ? null : filter.status();
        return new Filter(filter.userId(), filter.bookId(), filter.clubId(), status,
                keyword == null || keyword.isEmpty() ? null : keyword, filter.reportedOnly());
    }

    private static ModerationResolution resolutionOf(ContentAction action) {
        return switch (action) {
            case HIDE -> ModerationResolution.HIDE;
            case RESTORE -> ModerationResolution.KEEP;
            case DELETE -> ModerationResolution.DELETE;
        };
    }

    private static String statusAfter(Snap snap, ContentAction action) {
        return switch (action) {
            case HIDE -> Post.HIDDEN;
            case RESTORE -> Post.VISIBLE;
            case DELETE -> snap.statusful() ? Post.DELETED : "REMOVED";
        };
    }

    private static void requireContentType(ModerationSource type) {
        if (type == null || !CONTENT_TYPES.contains(type)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "콘텐츠 종류가 아닙니다.");
        }
    }

    private static List<Long> ids(Collection<Snap> snaps, Function<Snap, Long> getter) {
        return snaps.stream().map(getter).filter(Objects::nonNull).distinct().toList();
    }

    private static List<Long> idsOf(Collection<Snap> snaps, ModerationSource type, Function<Snap, Long> getter) {
        return snaps.stream().filter(s -> s.type() == type).map(getter).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static String truncate(String text, int max) {
        String value = nullToEmpty(text);
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
