package app.bookey.api.post;

import app.bookey.api.club.ClubService;
import app.bookey.api.post.dto.PostDtos.*;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.Club;
import app.bookey.domain.club.ClubMember;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostCommentRepository;
import app.bookey.domain.post.PostCommentRepository.PostCommentCount;
import app.bookey.domain.post.PostExcerpt;
import app.bookey.domain.post.PostImage;
import app.bookey.domain.post.PostImageRepository;
import app.bookey.domain.post.PostLike;
import app.bookey.domain.post.PostLikeRepository;
import app.bookey.domain.post.PostLikeRepository.PostLikeCount;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostRules;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 독후감 (§F7) — 작성 · 수정 · 삭제 · 내 목록 · 광장 피드 · 단건(조회수) · 좋아요 · 책별 · 공개 블로그 · 모임별.
 *
 * <p>본문은 마크다운이고, 모임 안에서 쓴 글은 clubId 를 가진다.
 * 형식·공개 범위 규칙은 {@link PostRules} 가 정한다. CLUB 공개 글은 작성자와 그 모임 활성 멤버만 읽는다.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    /** 도배 방지 — 1분에 5건. */
    private static final int CREATE_RATE_LIMIT = 5;
    /** 목록 카드에 보여줄 발췌 길이. */
    private static final int EXCERPT_LENGTH = 140;
    /** 같은 사람이 같은 글을 이 시간 안에 다시 열어도 조회수는 한 번만 센다. */
    private static final Duration VIEW_COUNT_WINDOW = Duration.ofHours(1);

    private final PostRepository postRepository;
    private final PostImageRepository imageRepository;
    private final PostLikeRepository likeRepository;
    private final PostCommentRepository commentRepository;
    private final BookRepository bookRepository;
    private final ReadingRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final ClubService clubService;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;

    @Transactional
    public PostView create(Long userId, CreatePostRequest request) {
        PostRules.requireVisibility(request.clubId() != null, request.visibility());
        PostRules.requireContent(true, request.bodyMd(), request.imageIds());
        if (request.clubId() != null) {
            requireOpenClubMember(userId, request.clubId());
        }
        Long readingRecordId = null;
        if (request.bookId() != null) {
            requireBook(request.bookId());
            // 독서 기록은 책이 있을 때만 의미가 있다 — 책 없이 온 readingRecordId 는 무시한다.
            readingRecordId = ownedRecordId(userId, request.readingRecordId());
        }
        rateLimiter.require("post:create:" + userId, CREATE_RATE_LIMIT, Duration.ofMinutes(1));

        Post post = postRepository.save(Post.builder()
                .userId(userId)
                .bookId(request.bookId())
                .readingRecordId(readingRecordId)
                .slug(uniqueSlug(userId, request.title()))
                .title(request.title())
                .bodyMd(request.bodyMd())
                .visibility(request.visibility())
                .tags(toTags(request.tags()))
                .clubId(request.clubId())
                .build());
        attachImages(post, userId, request.imageIds());
        return toView(post, userId);
    }

    /** null 필드는 유지, 빈 목록은 비움. 책은 바꿀 수만 있고 없앨 수는 없다. 모임은 바꿀 수 없다. */
    @Transactional
    public PostView update(Long userId, Long postId, UpdatePostRequest request) {
        Post post = owned(userId, postId);
        PostRules.requireVisibility(post.isClubPost(), request.visibility());
        PostRules.requireContent(false, request.bodyMd(), request.imageIds());
        if (request.bookId() != null && !request.bookId().equals(post.getBookId())) {
            requireBook(request.bookId());
            post.changeBook(request.bookId());
        }
        post.edit(request.title(), request.bodyMd(), toTags(request.tags()));
        if (request.visibility() != null) {
            post.changeVisibility(request.visibility());
        }
        if (request.imageIds() != null) {
            detachImagesNotIn(post, request.imageIds());
            attachImages(post, userId, request.imageIds());
        }
        return toView(post, userId);
    }

    /** 사진은 연결만 끊어 남기고(정리 배치가 지운다), 좋아요·댓글은 DB CASCADE 로 함께 지워진다. */
    @Transactional
    public void delete(Long userId, Long postId) {
        Post post = owned(userId, postId);
        imageRepository.detachAllByPostId(postId);
        postRepository.delete(post);
    }

    /** 독후감 한 건 — 비공개는 작성자만. 남의 글은 사람·글당 1시간에 한 번만 조회수를 올린다. */
    @Transactional
    public PostView get(Long viewerId, Long postId) {
        Post post = readable(viewerId, postId);
        if (!post.isOwnedBy(viewerId)
                && rateLimiter.tryAcquire("post:view:" + postId + ":" + viewerId, 1, VIEW_COUNT_WINDOW)) {
            post.increaseView();
        }
        return toView(post, viewerId);
    }

    /** 광장 독후감 피드 (§14.1) — HOT(기본): 좋아요·시간 감쇠 점수, NEW: 최신순. */
    @Transactional(readOnly = true)
    public PageResponse<PostView> feed(Long viewerId, FeedSort sort, Pageable pageable) {
        Page<Post> page = sort == FeedSort.NEW
                ? postRepository.findFeed(pageable)
                : postRepository.findHotFeed(pageable);
        return toPage(page, viewerId);
    }

    /** 유저 마이페이지의 공개 독후감 (§14.3) — 피드에서 휘발된 글도 여기엔 축적된다. */
    @Transactional(readOnly = true)
    public PageResponse<PostView> listPublicByUser(Long viewerId, Long userId, Pageable pageable) {
        return toPage(postRepository.findAllByUserIdAndVisibilityOrderByPublishedAtDescIdDesc(
                userId, PostVisibility.PUBLIC, pageable), viewerId);
    }

    /** 내 독후감 — 모임 독후감까지 전부. */
    @Transactional(readOnly = true)
    public PageResponse<PostView> listMine(Long userId, Pageable pageable) {
        return toPage(postRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable), userId);
    }

    /** 책 상세의 공개 독후감 — 로그인 사용자용(likedByMe·mine 반영). */
    @Transactional(readOnly = true)
    public PageResponse<PostView> listByBook(Long viewerId, Long bookId, Pageable pageable) {
        requireBook(bookId);
        return toPage(postRepository.findAllByBookIdAndVisibilityOrderByPublishedAtDescIdDesc(
                bookId, PostVisibility.PUBLIC, pageable), viewerId);
    }

    /** 모임 독후감 — 그 모임 활성 멤버만, 모임에서 쓴 글(PUBLIC·CLUB) 최신순. 끝난 모임도 읽을 수는 있다. */
    @Transactional(readOnly = true)
    public PageResponse<PostView> listByClub(Long viewerId, Long clubId, Pageable pageable) {
        clubService.getClub(clubId);
        clubService.activeMember(clubId, viewerId);
        return toPage(postRepository.findAllByClubIdOrderByCreatedAtDescIdDesc(clubId, pageable), viewerId);
    }

    /** 좋아요 토글 — BookService.toggleLike 미러. 읽을 수 없는 글은 없는 것으로 본다. */
    @Transactional
    public PostLikeView toggleLike(Long userId, Long postId) {
        Post post = readable(userId, postId);
        var existing = likeRepository.findByUserIdAndPostId(userId, postId);
        boolean liked;
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            liked = false;
        } else {
            likeRepository.save(PostLike.builder().userId(userId).postId(postId).build());
            liked = true;
            notifyPostLiked(userId, post);
        }
        return new PostLikeView(liked, likeRepository.countByPostId(postId));
    }

    /** 공개 블로그 — bookey.app/@{handle} (§F7 SEO 유입). 비회원이므로 likedByMe·mine 은 false. */
    @Transactional(readOnly = true)
    public PageResponse<PostView> listPublicByHandle(String handle, Pageable pageable) {
        User user = userRepository.findByHandle(handle)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return toPage(postRepository.findAllByUserIdAndVisibilityOrderByPublishedAtDescIdDesc(
                user.getId(), PostVisibility.PUBLIC, pageable), null);
    }

    @Transactional
    public PostView readPublic(String handle, String slug) {
        User user = userRepository.findByHandle(handle)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Post post = postRepository.findByUserIdAndSlug(user.getId(), slug)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        // 공개 블로그는 비회원 경로라 비공개·모임 공개 글은 없는 것으로 본다.
        if (post.getVisibility() == PostVisibility.PRIVATE || post.getVisibility() == PostVisibility.CLUB) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        post.increaseView();
        return toView(post, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostView> listPublicByBook(Long bookId, Pageable pageable) {
        return toPage(postRepository.findAllByBookIdAndVisibilityOrderByPublishedAtDescIdDesc(
                bookId, PostVisibility.PUBLIC, pageable), null);
    }

    // ────────────────────────────── 검증 ──────────────────────────────

    private Post owned(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.of(ErrorCode.POST_NOT_FOUND));
        if (!post.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        return post;
    }

    /**
     * 없는 글과 읽을 수 없는 글은 똑같이 POST_NOT_FOUND — 비공개 글의 존재를 드러내지 않는다.
     * 댓글도 같은 규칙을 써야 하므로 package-private 로 열어 {@link PostCommentService} 가 함께 쓴다.
     */
    Post readable(Long viewerId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.of(ErrorCode.POST_NOT_FOUND));
        if (!post.isReadableBy(viewerId, isClubReader(viewerId, post))) {
            throw ApiException.of(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    /** CLUB 공개 글일 때만 멤버십을 조회한다 — 나머지 공개 범위는 멤버 여부와 무관하다. */
    private boolean isClubReader(Long viewerId, Post post) {
        if (post.getVisibility() != PostVisibility.CLUB || viewerId == null || post.getClubId() == null) {
            return false;
        }
        return clubMemberRepository.findByClubIdAndUserId(post.getClubId(), viewerId)
                .filter(ClubMember::isActive)
                .isPresent();
    }

    /** 모임 독후감은 그 모임 활성 멤버가 진행 중인 모임에서만 쓴다 — 없으면 404, 멤버가 아니면 403, 끝났으면 409. */
    private void requireOpenClubMember(Long userId, Long clubId) {
        Club club = clubService.getClub(clubId);
        clubService.activeMember(clubId, userId);
        if (club.getStatus().isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }
    }

    private void requireBook(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw ApiException.of(ErrorCode.BOOK_NOT_FOUND);
        }
    }

    /** 독서 기록은 존재하고 내 것이어야 한다. */
    private Long ownedRecordId(Long userId, Long readingRecordId) {
        if (readingRecordId == null) {
            return null;
        }
        ReadingRecord record = recordRepository.findById(readingRecordId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
        if (!record.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        return readingRecordId;
    }

    private void notifyPostLiked(Long likerId, Post post) {
        if (post.isOwnedBy(likerId)) {
            return;
        }
        User liker = userRepository.findById(likerId).orElse(null);
        String nickname = liker == null ? "누군가" : liker.getNickname();
        notificationService.inApp(new NotificationService.NotificationRequest(
                post.getUserId(), NotificationType.POST_LIKED, null, post.getReadingRecordId(), null,
                "내 독후감에 좋아요가 달렸어요",
                nickname + "님이 \"" + post.getTitle() + "\"에 좋아요를 눌렀어요.",
                Map.of("postId", post.getId(), "fromUserId", likerId), null));
    }

    /** 사진을 요청 순서대로 붙인다. null·빈 목록이면 아무것도 하지 않는다. 중복 id 는 첫 것만. */
    private void attachImages(Post post, Long userId, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        List<Long> ids = imageIds.stream().filter(Objects::nonNull).distinct().toList();
        List<PostImage> found = imageRepository.findAllById(ids);
        validateImageAttachments(userId, post.getId(), ids, found);
        Map<Long, PostImage> images = found.stream()
                .collect(Collectors.toMap(PostImage::getId, Function.identity()));
        for (int order = 0; order < ids.size(); order++) {
            images.get(ids.get(order)).attach(post.getId(), order);
        }
    }

    /**
     * 요청한 사진이 모두 존재하고, 전부 내 것이며, 다른 독후감에 붙어 있지 않아야 한다.
     * 세 경우 모두 POST_IMAGE_NOT_FOUND — 없는 것·남의 것·남의 글에 붙은 것을 구분해 알리지 않는다.
     * 같은 독후감에 이미 붙은 사진은 허용한다(수정 때 유지되는 사진).
     */
    static void validateImageAttachments(Long userId, Long postId, List<Long> requestedIds, List<PostImage> found) {
        Set<Long> foundIds = found.stream().map(PostImage::getId).collect(Collectors.toSet());
        if (!foundIds.containsAll(requestedIds)) {
            throw ApiException.of(ErrorCode.POST_IMAGE_NOT_FOUND);
        }
        for (PostImage image : found) {
            boolean attachedElsewhere = image.getPostId() != null && !image.getPostId().equals(postId);
            if (!image.isOwnedBy(userId) || attachedElsewhere) {
                throw ApiException.of(ErrorCode.POST_IMAGE_NOT_FOUND);
            }
        }
    }

    /** 수정 때 목록에서 빠진 사진은 뗀다 — 사진 자체는 남겨 두고 정리 배치가 지운다. */
    private void detachImagesNotIn(Post post, List<Long> keptIds) {
        Set<Long> kept = new HashSet<>(keptIds);
        imageRepository.findAllByPostIdInOrderBySortOrderAscIdAsc(List.of(post.getId())).stream()
                .filter(image -> !kept.contains(image.getId()))
                .forEach(PostImage::detach);
    }

    private static String[] toTags(List<String> tags) {
        return tags == null ? null : tags.toArray(String[]::new);
    }

    private String uniqueSlug(Long userId, String title) {
        String base = slugify(title);
        String candidate = base;
        int suffix = 2;
        while (postRepository.existsByUserIdAndSlug(userId, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String slugify(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "post";
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    // ────────────────────────────── 조립 ──────────────────────────────

    private PageResponse<PostView> toPage(Page<Post> page, Long viewerId) {
        List<PostView> views = assemble(page.getContent(), viewerId);
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /** 단건도 목록과 같은 경로로 조립한다. */
    private PostView toView(Post post, Long viewerId) {
        return assemble(List.of(post), viewerId).get(0);
    }

    /** 페이지 하나에 쿼리 수가 고정되도록 id 목록으로 한 번씩만 읽어 조립한다(PlazaService 선례). */
    private List<PostView> assemble(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Book> books = loadBooks(posts.stream().map(Post::getBookId).filter(Objects::nonNull).distinct().toList());
        Map<Long, User> authors = loadAuthors(posts.stream().map(Post::getUserId).distinct().toList());
        Map<Long, Club> clubs = loadClubs(posts.stream().map(Post::getClubId).filter(Objects::nonNull).distinct().toList());
        return assembleViews(posts, viewerId, books, authors, clubs, loadImages(postIds),
                loadLikeCounts(postIds), loadMyLiked(viewerId, postIds), loadCommentCounts(postIds));
    }

    private Map<Long, Book> loadBooks(List<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
    }

    private Map<Long, Club> loadClubs(List<Long> clubIds) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        return clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));
    }

    private Map<Long, User> loadAuthors(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 독후감별 사진 — sort_order 순. */
    private Map<Long, List<PostImage>> loadImages(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return imageRepository.findAllByPostIdInOrderBySortOrderAscIdAsc(postIds).stream()
                .collect(Collectors.groupingBy(PostImage::getPostId));
    }

    private Map<Long, Long> loadLikeCounts(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return likeRepository.countPerPost(postIds).stream()
                .collect(Collectors.toMap(PostLikeCount::getPostId, PostLikeCount::getLikeCount));
    }

    /** 비로그인 조회자(null)는 빈 집합. */
    private Set<Long> loadMyLiked(Long viewerId, List<Long> postIds) {
        if (viewerId == null || postIds.isEmpty()) {
            return Set.of();
        }
        return likeRepository.findAllByUserIdAndPostIdIn(viewerId, postIds).stream()
                .map(PostLike::getPostId)
                .collect(Collectors.toSet());
    }

    private Map<Long, Long> loadCommentCounts(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countPerPost(postIds).stream()
                .collect(Collectors.toMap(PostCommentCount::getPostId, PostCommentCount::getCommentCount));
    }

    /**
     * 배치 맵으로 뷰를 조립한다. 입력 순서를 지킨다.
     * likeCount·commentCount 결측은 0, 사진 결측은 빈 목록, 밑줄(quotes)은 기능을 걷어내 늘 빈 목록, 탈퇴한 작성자는 "알 수 없음",
     * 책 결측은 제목·표지만 null(bookId 는 그대로), 모임 결측도 이름만 null. 비로그인 조회자(null)는 mine·likedByMe 가 false.
     */
    static List<PostView> assembleViews(List<Post> posts, Long viewerId,
                                        Map<Long, Book> books, Map<Long, User> authors, Map<Long, Club> clubs,
                                        Map<Long, List<PostImage>> imagesByPost,
                                        Map<Long, Long> likeCounts, Set<Long> myLiked,
                                        Map<Long, Long> commentCounts) {
        return posts.stream()
                .map(post -> {
                    Book book = post.getBookId() == null ? null : books.get(post.getBookId());
                    User author = authors.get(post.getUserId());
                    Club club = post.getClubId() == null ? null : clubs.get(post.getClubId());
                    List<PostImageView> images = imagesByPost.getOrDefault(post.getId(), List.of()).stream()
                            .map(image -> new PostImageView(image.getId(), image.getUrl(),
                                    image.getWidth(), image.getHeight()))
                            .toList();
                    return new PostView(
                            post.getId(), post.getSlug(), post.getTitle(), post.getBodyMd(),
                            post.getVisibility(), Arrays.asList(post.getTags()),
                            post.getBookId(),
                            book == null ? null : book.getTitle(),
                            book == null ? null : book.getCoverUrl(),
                            author == null ? null : author.getHandle(),
                            author == null ? "알 수 없음" : author.getNickname(),
                            post.getPublishedAt(), post.getViewCount(),
                            post.getUserId(),
                            author == null ? null : author.getAvatarUrl(),
                            PostExcerpt.of(post.getBodyMd(), EXCERPT_LENGTH),
                            images,
                            List.of(),
                            likeCounts.getOrDefault(post.getId(), 0L),
                            myLiked.contains(post.getId()),
                            commentCounts.getOrDefault(post.getId(), 0L),
                            post.isOwnedBy(viewerId),
                            post.getCreatedAt() == null ? Instant.now() : post.getCreatedAt(),
                            post.getClubId(),
                            club == null ? null : club.getName());
                })
                .toList();
    }
}
