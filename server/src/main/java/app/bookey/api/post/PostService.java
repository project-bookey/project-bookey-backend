package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.*;
import app.bookey.api.quote.QuoteService;
import app.bookey.api.quote.dto.QuoteDtos.BookQuoteView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostCommentRepository;
import app.bookey.domain.post.PostCommentRepository.PostCommentCount;
import app.bookey.domain.post.PostExcerpt;
import app.bookey.domain.post.PostImage;
import app.bookey.domain.post.PostImageRepository;
import app.bookey.domain.post.PostLike;
import app.bookey.domain.post.PostLikeRepository;
import app.bookey.domain.post.PostLikeRepository.PostLikeCount;
import app.bookey.domain.post.PostQuote;
import app.bookey.domain.post.PostQuoteRepository;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.quote.BookQuote;
import app.bookey.domain.quote.BookQuoteRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 독후감 (§F7) — 작성 · 수정 · 삭제 · 내 목록 · 광장 피드 · 단건(조회수) · 좋아요 · 책별 · 공개 블로그. */
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
    private final PostQuoteRepository postQuoteRepository;
    private final PostLikeRepository likeRepository;
    private final PostCommentRepository commentRepository;
    private final BookQuoteRepository quoteRepository;
    private final BookRepository bookRepository;
    private final ReadingRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final QuoteService quoteService;
    private final RateLimiter rateLimiter;

    @Transactional
    public PostView create(Long userId, CreatePostRequest request) {
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
                .build());
        attachImages(post, userId, request.imageIds());
        attachQuotes(post, userId, request.quoteIds());
        return toView(post, userId);
    }

    /** null 필드는 유지, 빈 목록은 비움. 책은 바꿀 수만 있고 없앨 수는 없다. */
    @Transactional
    public PostView update(Long userId, Long postId, UpdatePostRequest request) {
        Post post = owned(userId, postId);
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
        if (request.quoteIds() != null) {
            // 연결을 전부 지우고 다시 넣는다. IDENTITY 채번은 persist 때 바로 INSERT 하므로
            // 삭제를 먼저 flush 해야 같은 밑줄을 다시 붙일 때 (post_id, quote_id) 유니크에 걸리지 않는다.
            postQuoteRepository.deleteAllByPostId(postId);
            postQuoteRepository.flush();
            attachQuotes(post, userId, request.quoteIds());
        }
        return toView(post, userId);
    }

    /** 사진은 연결만 끊어 남기고(정리 배치가 지운다), 밑줄 연결·좋아요·댓글은 DB CASCADE 로 함께 지워진다. */
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

    /** 광장 독후감 피드 — 공개 독후감 최신순. */
    @Transactional(readOnly = true)
    public PageResponse<PostView> feed(Long viewerId, Pageable pageable) {
        return toPage(postRepository.findFeed(pageable), viewerId);
    }

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

    /** 좋아요 토글 — BookService.toggleLike 미러. 읽을 수 없는 글은 없는 것으로 본다. */
    @Transactional
    public PostLikeView toggleLike(Long userId, Long postId) {
        readable(userId, postId);
        var existing = likeRepository.findByUserIdAndPostId(userId, postId);
        boolean liked;
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            liked = false;
        } else {
            likeRepository.save(PostLike.builder().userId(userId).postId(postId).build());
            liked = true;
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
        if (post.getVisibility() == PostVisibility.PRIVATE) {
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

    /** 없는 글과 읽을 수 없는 글은 똑같이 POST_NOT_FOUND — 비공개 글의 존재를 드러내지 않는다. */
    private Post readable(Long viewerId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.of(ErrorCode.POST_NOT_FOUND));
        if (!post.isReadableBy(viewerId)) {
            throw ApiException.of(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void requireBook(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw ApiException.of(ErrorCode.BOOK_NOT_FOUND);
        }
    }

    /** 독서 기록은 존재하고 내 것이어야 한다(QuoteService.create 선례). */
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

    /**
     * 사진을 요청 순서대로 붙인다. null·빈 목록이면 아무것도 하지 않는다.
     * 없는 id·남의 사진·다른 독후감에 이미 붙은 사진은 모두 POST_IMAGE_NOT_FOUND — 존재를 드러내지 않는다.
     */
    private void attachImages(Post post, Long userId, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        List<Long> ids = imageIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, PostImage> images = imageRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PostImage::getId, Function.identity()));
        if (images.size() != ids.size()) {
            throw ApiException.of(ErrorCode.POST_IMAGE_NOT_FOUND);
        }
        for (int order = 0; order < ids.size(); order++) {
            PostImage image = images.get(ids.get(order));
            boolean attachedElsewhere = image.getPostId() != null && !image.getPostId().equals(post.getId());
            if (!image.isOwnedBy(userId) || attachedElsewhere) {
                throw ApiException.of(ErrorCode.POST_IMAGE_NOT_FOUND);
            }
            image.attach(post.getId(), order);
        }
    }

    /** 수정 때 목록에서 빠진 사진은 뗀다 — 사진 자체는 남겨 두고 정리 배치가 지운다. */
    private void detachImagesNotIn(Post post, List<Long> keptIds) {
        Set<Long> kept = new HashSet<>(keptIds);
        imageRepository.findAllByPostIdInOrderBySortOrderAscIdAsc(List.of(post.getId())).stream()
                .filter(image -> !kept.contains(image.getId()))
                .forEach(PostImage::detach);
    }

    /** 밑줄을 요청 순서대로 잇는다. 소유만 검사한다 — 책이 없는 글에도, 다른 책의 밑줄도 붙일 수 있다. 중복 id 는 첫 것만. */
    private void attachQuotes(Post post, Long userId, List<Long> quoteIds) {
        if (quoteIds == null || quoteIds.isEmpty()) {
            return;
        }
        List<Long> ids = quoteIds.stream().filter(Objects::nonNull).distinct().toList();
        validateQuoteAttachments(userId, ids, quoteRepository.findAllById(ids));
        List<PostQuote> links = new ArrayList<>(ids.size());
        for (int order = 0; order < ids.size(); order++) {
            links.add(PostQuote.builder()
                    .postId(post.getId()).quoteId(ids.get(order)).sortOrder((short) order)
                    .build());
        }
        postQuoteRepository.saveAll(links);
    }

    /** 요청한 밑줄이 모두 존재하고 전부 내 것이어야 한다 — 없는 것과 남의 것을 구분해 알리지 않는다. */
    static void validateQuoteAttachments(Long userId, List<Long> requestedIds, List<BookQuote> found) {
        Set<Long> foundIds = found.stream().map(BookQuote::getId).collect(Collectors.toSet());
        boolean allFound = foundIds.containsAll(requestedIds);
        boolean allMine = found.stream().allMatch(quote -> quote.isOwnedBy(userId));
        if (!allFound || !allMine) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "내가 오려둔 밑줄만 붙일 수 있습니다.");
        }
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

    /** 페이지 하나에 쿼리 수가 고정되도록 id 목록으로 한 번씩만 읽어 조립한다(QuoteService·PlazaService 선례). */
    private List<PostView> assemble(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Book> books = loadBooks(posts.stream().map(Post::getBookId).filter(Objects::nonNull).distinct().toList());
        Map<Long, User> authors = loadAuthors(posts.stream().map(Post::getUserId).distinct().toList());
        return assembleViews(posts, viewerId, books, authors,
                loadImages(postIds), loadQuoteViews(postIds, viewerId, books),
                loadLikeCounts(postIds), loadMyLiked(viewerId, postIds), loadCommentCounts(postIds));
    }

    private Map<Long, Book> loadBooks(List<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
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

    /** 독후감별 인용 밑줄 id — sort_order 순. */
    private Map<Long, List<Long>> loadPostQuotes(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return postQuoteRepository.findAllByPostIdInOrderBySortOrderAscIdAsc(postIds).stream()
                .collect(Collectors.groupingBy(PostQuote::getPostId, LinkedHashMap::new,
                        Collectors.mapping(PostQuote::getQuoteId, Collectors.toList())));
    }

    /** 인용된 밑줄을 한 번에 읽어 BookQuoteView 로 조립하고 독후감별 순서대로 묶는다. 이미 읽은 책은 넘겨 재조회를 피한다. */
    private Map<Long, List<BookQuoteView>> loadQuoteViews(List<Long> postIds, Long viewerId, Map<Long, Book> books) {
        Map<Long, List<Long>> quoteIdsByPost = loadPostQuotes(postIds);
        if (quoteIdsByPost.isEmpty()) {
            return Map.of();
        }
        List<Long> quoteIds = quoteIdsByPost.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, BookQuoteView> views = quoteService.viewsOf(quoteRepository.findAllById(quoteIds), viewerId, books);
        Map<Long, List<BookQuoteView>> byPost = new HashMap<>();
        quoteIdsByPost.forEach((postId, ids) -> byPost.put(postId,
                ids.stream().map(views::get).filter(Objects::nonNull).toList()));
        return byPost;
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
     * 배치 맵으로 뷰를 조립한다(QuoteService.assembleViews 선례). 입력 순서를 지킨다.
     * likeCount·commentCount 결측은 0, 사진·밑줄 결측은 빈 목록, 탈퇴한 작성자는 "알 수 없음",
     * 책 결측은 제목·표지만 null(bookId 는 그대로). 비로그인 조회자(null)는 mine·likedByMe 가 false.
     */
    static List<PostView> assembleViews(List<Post> posts, Long viewerId,
                                        Map<Long, Book> books, Map<Long, User> authors,
                                        Map<Long, List<PostImage>> imagesByPost,
                                        Map<Long, List<BookQuoteView>> quotesByPost,
                                        Map<Long, Long> likeCounts, Set<Long> myLiked,
                                        Map<Long, Long> commentCounts) {
        return posts.stream()
                .map(post -> {
                    Book book = post.getBookId() == null ? null : books.get(post.getBookId());
                    User author = authors.get(post.getUserId());
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
                            quotesByPost.getOrDefault(post.getId(), List.of()),
                            likeCounts.getOrDefault(post.getId(), 0L),
                            myLiked.contains(post.getId()),
                            commentCounts.getOrDefault(post.getId(), 0L),
                            post.isOwnedBy(viewerId),
                            post.getCreatedAt() == null ? Instant.now() : post.getCreatedAt());
                })
                .toList();
    }
}
