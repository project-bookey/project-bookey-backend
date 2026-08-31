package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.*;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.admin.ModerationTicket;
import app.bookey.domain.admin.ModerationTicketRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingStatus;
import app.bookey.domain.report.AbuseReport;
import app.bookey.domain.report.AbuseReportRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모임 토론 (§12.3) + 스포일러 가드 (§8.5).
 *
 * <p>마스킹은 <b>서버에서 본문을 제거</b>하는 방식이다. 클라이언트 블러는 UX 일 뿐,
 * 가려진 글의 본문은 애초에 응답에 담기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ClubPostService {

    /** 도배 방지 — 1분에 10건 (§8.5). */
    private static final int POST_RATE_LIMIT = 10;

    private final ClubPostRepository postRepository;
    private final ClubPostReactionRepository reactionRepository;
    private final ClubPostRevealRepository revealRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubBookRepository clubBookRepository;
    private final ClubEventRepository eventRepository;
    private final ReadingRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final AbuseReportRepository abuseReportRepository;
    private final ModerationTicketRepository moderationTicketRepository;
    private final ClubService clubService;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;

    /** 뷰어의 진도 상태 — 마스킹 판정 입력값. */
    private record ViewerState(int currentPage, boolean finished) {}

    @Transactional(readOnly = true)
    public PageResponse<PostView> feed(Long userId, Long clubId, boolean onlyMyRange,
                                       Pageable pageable) {
        ClubMember me = clubService.activeMember(clubId, userId);
        ViewerState viewer = viewerState(me);

        // "내 진도까지만 보기"가 켜지면 앞선 앵커 글은 목록에서 아예 빼준다.
        Page<ClubPost> page = onlyMyRange && !viewer.finished()
                ? postRepository.findFeedUpTo(clubId, viewer.currentPage(), pageable)
                : postRepository.findFeed(clubId, pageable);

        List<ClubPost> posts = page.getContent();
        List<Long> postIds = posts.stream().map(ClubPost::getId).toList();

        Map<Long, List<ClubPost>> commentsByParent = postIds.isEmpty()
                ? Map.of()
                : postRepository.findAllByParentIdInAndStatus(postIds, "VISIBLE").stream()
                        .collect(Collectors.groupingBy(ClubPost::getParentId));

        List<ClubPost> all = new ArrayList<>(posts);
        commentsByParent.values().forEach(all::addAll);

        Map<Long, User> authors = loadAuthors(all);
        Set<Long> revealed = loadRevealed(userId, all);
        Map<Long, List<String>> myReactions = loadMyReactions(userId, all);

        return PageResponse.of(page, post -> toView(post, viewer, userId, authors, revealed,
                myReactions, commentsByParent.getOrDefault(post.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PostView detail(Long userId, Long clubId, Long postId) {
        ClubMember me = clubService.activeMember(clubId, userId);
        ViewerState viewer = viewerState(me);
        ClubPost post = getPost(clubId, postId);

        List<ClubPost> comments =
                postRepository.findAllByParentIdAndStatusOrderByCreatedAtAsc(postId, "VISIBLE");
        List<ClubPost> all = new ArrayList<>(comments);
        all.add(post);

        return toView(post, viewer, userId, loadAuthors(all), loadRevealed(userId, all),
                loadMyReactions(userId, all), comments);
    }

    /** "그래도 볼래요" — 개별 글 스포일러 해제. 감사 로그를 남긴다(§8.5). */
    @Transactional
    public PostView reveal(Long userId, Long clubId, Long postId) {
        ClubMember me = clubService.activeMember(clubId, userId);
        ClubPost post = getPost(clubId, postId);

        if (!revealRepository.existsByClubPostIdAndUserId(postId, userId)) {
            ViewerState viewer = viewerState(me);
            revealRepository.save(new ClubPostReveal(postId, userId, viewer.currentPage()));
        }
        return detail(userId, clubId, postId);
    }

    @Transactional
    public PostView create(Long userId, Long clubId, CreatePostRequest request) {
        ClubMember me = clubService.activeMember(clubId, userId);
        Club club = clubService.getClub(clubId);
        if (club.getStatus().isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }
        rateLimiter.require("club:post:" + userId, POST_RATE_LIMIT, Duration.ofMinutes(1));

        ClubPostType type = request.type() == null ? ClubPostType.DISCUSSION : request.type();
        if (type.requiresModerator() && !me.canModerate()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "공지는 호스트·운영자만 작성할 수 있습니다.");
        }
        if (type.requiresAnchorPage() && request.anchorPage() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "인용은 페이지를 함께 남겨야 합니다.");
        }

        ClubPost parent = null;
        if (request.parentId() != null) {
            parent = getPost(clubId, request.parentId());
            if (parent.isComment()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "대댓글은 1단계까지만 가능합니다.");
            }
        }

        Long clubBookId = clubBookRepository.findFirstByClubIdOrderBySeqAsc(clubId)
                .map(ClubBook::getId)
                .orElse(null);

        ClubPost post = postRepository.save(ClubPost.builder()
                .clubId(clubId)
                .clubBookId(clubBookId)
                .userId(userId)
                .parentId(request.parentId())
                .type(type)
                .body(request.body())
                .anchorPage(request.anchorPage())
                .spoilerLevel(request.spoilerLevel())
                .linkedPostId(request.linkedPostId())
                .build());

        if (parent != null) {
            parent.increaseComment();
            notifyComment(parent, userId, clubId, post);
        } else {
            eventRepository.save(new ClubEvent(clubId, userId, ClubEventType.POSTED,
                    Map.of("postId", post.getId(), "type", type.name())));
            notifyNewPost(club, post, userId);
        }

        ViewerState viewer = viewerState(me);
        return toView(post, viewer, userId, loadAuthors(List.of(post)), Set.of(), Map.of(), List.of());
    }

    /** 내 앵커 범위 안에 있는 멤버에게만 새 토론 알림을 보낸다(§12.4). */
    private void notifyNewPost(Club club, ClubPost post, Long authorId) {
        List<ClubMember> members =
                memberRepository.findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE);
        for (ClubMember member : members) {
            if (member.getUserId().equals(authorId)) {
                continue;
            }
            ViewerState state = viewerState(member);
            if (post.isMaskedFor(state.currentPage(), state.finished(), false)) {
                continue;   // 아직 진도가 안 된 사람에겐 알리지 않는다 — 알림 자체가 스포일러다
            }
            notificationService.schedule(new NotificationService.NotificationRequest(
                    member.getUserId(), NotificationType.CLUB_NEW_POST, null, null, club.getId(),
                    club.getName(), "새 토론이 올라왔어요",
                    Map.of("clubId", club.getId(), "postId", post.getId()), null));
        }
    }

    private void notifyComment(ClubPost parent, Long authorId, Long clubId, ClubPost comment) {
        if (parent.getUserId().equals(authorId)) {
            return;
        }
        notificationService.schedule(new NotificationService.NotificationRequest(
                parent.getUserId(), NotificationType.CLUB_NEW_POST, null, null, clubId,
                "내 글에 댓글이 달렸어요", "모임 토론을 확인해 보세요",
                Map.of("clubId", clubId, "postId", parent.getId(), "commentId", comment.getId()),
                null));
    }

    @Transactional
    public void delete(Long userId, Long clubId, Long postId) {
        ClubMember me = clubService.activeMember(clubId, userId);
        ClubPost post = getPost(clubId, postId);
        if (!post.isAuthor(userId) && !me.canModerate()) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        post.softDelete();
        if (post.isComment()) {
            postRepository.findById(post.getParentId()).ifPresent(ClubPost::decreaseComment);
        }
    }

    @Transactional
    public void pin(Long userId, Long clubId, Long postId, boolean pinned) {
        ClubMember me = clubService.activeMember(clubId, userId);
        if (!me.canModerate()) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        getPost(clubId, postId).pin(pinned);
    }

    @Transactional
    public void react(Long userId, Long clubId, Long postId, ReactionKind kind) {
        clubService.activeMember(clubId, userId);
        ClubPost post = getPost(clubId, postId);
        reactionRepository.findByClubPostIdAndUserIdAndKind(postId, userId, kind)
                .ifPresentOrElse(
                        existing -> {
                            reactionRepository.delete(existing);
                            post.changeReactionCount(-1);
                        },
                        () -> {
                            reactionRepository.save(new ClubPostReaction(postId, userId, kind));
                            post.changeReactionCount(1);
                        });
    }

    /** 신고 → 관리자 큐로 승격. 3건 누적 시 임시 비노출(§8.3). */
    @Transactional
    public void report(Long userId, Long clubId, Long postId, ReportRequest request) {
        clubService.activeMember(clubId, userId);
        ClubPost post = getPost(clubId, postId);
        if (post.isAuthor(userId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "자기 글은 신고할 수 없습니다.");
        }
        if (abuseReportRepository.existsByTargetTypeAndTargetIdAndReporterId(
                "CLUB_POST", postId, userId)) {
            throw ApiException.of(ErrorCode.CONFLICT);
        }
        abuseReportRepository.save(
                new AbuseReport("CLUB_POST", postId, userId, request.reason(), request.detail()));
        post.addReport();

        ModerationTicket ticket = moderationTicketRepository
                .findBySourceTypeAndSourceId(ModerationSource.CLUB_POST, postId)
                .orElseGet(() -> moderationTicketRepository.save(
                        new ModerationTicket(ModerationSource.CLUB_POST, postId, request.reason())));
        if (ticket.getReportCount() < post.getReportCount()) {
            ticket.addReport();
        }
        if (ticket.shouldAutoHide()) {
            post.hide();
        }
    }

    // ────────────────────────────── 내부 ──────────────────────────────

    private ClubPost getPost(Long clubId, Long postId) {
        ClubPost post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!post.getClubId().equals(clubId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return post;
    }

    private ViewerState viewerState(ClubMember member) {
        if (member.getReadingRecordId() == null) {
            return new ViewerState(0, false);
        }
        ReadingRecord record = recordRepository.findById(member.getReadingRecordId()).orElse(null);
        if (record == null) {
            return new ViewerState(0, false);
        }
        return new ViewerState(record.getCurrentPage(), record.getStatus() == ReadingStatus.FINISHED);
    }

    private Map<Long, User> loadAuthors(List<ClubPost> posts) {
        List<Long> ids = posts.stream().map(ClubPost::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Set<Long> loadRevealed(Long userId, List<ClubPost> posts) {
        List<Long> ids = posts.stream().map(ClubPost::getId).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return revealRepository.findAllByUserIdAndClubPostIdIn(userId, ids).stream()
                .map(ClubPostReveal::getClubPostId)
                .collect(Collectors.toSet());
    }

    private Map<Long, List<String>> loadMyReactions(Long userId, List<ClubPost> posts) {
        List<Long> ids = posts.stream().map(ClubPost::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return reactionRepository.findAllByClubPostIdInAndUserId(ids, userId).stream()
                .collect(Collectors.groupingBy(ClubPostReaction::getClubPostId,
                        Collectors.mapping(r -> r.getKind().name(), Collectors.toList())));
    }

    private PostView toView(ClubPost post, ViewerState viewer, Long viewerId,
                            Map<Long, User> authors, Set<Long> revealed,
                            Map<Long, List<String>> myReactions, List<ClubPost> comments) {
        boolean isAuthor = post.isAuthor(viewerId);
        boolean masked = post.isMaskedFor(viewer.currentPage(), viewer.finished(), isAuthor)
                && !revealed.contains(post.getId());

        User author = authors.get(post.getUserId());
        List<PostView> commentViews = masked
                ? List.of()
                : comments.stream()
                        .map(c -> toView(c, viewer, viewerId, authors, revealed, myReactions, List.of()))
                        .toList();

        return new PostView(
                post.getId(),
                post.getParentId(),
                post.getType(),
                post.getUserId(),
                author == null ? "알 수 없음" : author.getNickname(),
                author == null ? null : author.getAvatarUrl(),
                masked ? null : post.getBody(),          // 본문은 아예 내려보내지 않는다
                masked,
                post.getAnchorPage(),
                post.getSpoilerLevel(),
                post.isPinned(),
                post.getCommentCount(),
                post.getReactionCount(),
                myReactions.getOrDefault(post.getId(), List.of()),
                post.getCreatedAt() == null ? Instant.now() : post.getCreatedAt(),
                commentViews);
    }
}
