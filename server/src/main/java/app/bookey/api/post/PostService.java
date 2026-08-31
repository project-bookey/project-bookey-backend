package app.bookey.api.post;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostRepository;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 독후감 (§F7). 기본 공개 범위는 비공개. */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public record CreatePostRequest(
            Long bookId,
            Long readingRecordId,
            @NotBlank @Size(max = 300) String title,
            @NotBlank String bodyMd,
            PostVisibility visibility,
            List<String> tags
    ) {}

    public record UpdatePostRequest(
            @Size(max = 300) String title,
            String bodyMd,
            List<String> tags,
            PostVisibility visibility
    ) {}

    public record PostView(
            Long id,
            String slug,
            String title,
            String bodyMd,
            PostVisibility visibility,
            List<String> tags,
            Long bookId,
            String bookTitle,
            String bookCoverUrl,
            String authorHandle,
            String authorNickname,
            Instant publishedAt,
            int viewCount
    ) {}

    @Transactional
    public PostView create(Long userId, CreatePostRequest request) {
        String slug = uniqueSlug(userId, request.title());
        Post post = postRepository.save(Post.builder()
                .userId(userId)
                .bookId(request.bookId())
                .readingRecordId(request.readingRecordId())
                .slug(slug)
                .title(request.title())
                .bodyMd(request.bodyMd())
                .visibility(request.visibility())
                .tags(request.tags() == null ? new String[0] : request.tags().toArray(String[]::new))
                .build());
        return toView(post);
    }

    @Transactional
    public PostView update(Long userId, Long postId, UpdatePostRequest request) {
        Post post = owned(userId, postId);
        post.edit(request.title(), request.bodyMd(),
                request.tags() == null ? null : request.tags().toArray(String[]::new));
        if (request.visibility() != null) {
            post.changeVisibility(request.visibility());
        }
        return toView(post);
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        postRepository.delete(owned(userId, postId));
    }

    @Transactional(readOnly = true)
    public PageResponse<PostView> listMine(Long userId, Pageable pageable) {
        return PageResponse.of(postRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable),
                this::toView);
    }

    /** 공개 블로그 — bookey.app/@{handle} (§F7 SEO 유입). */
    @Transactional(readOnly = true)
    public PageResponse<PostView> listPublicByHandle(String handle, Pageable pageable) {
        User user = userRepository.findByHandle(handle)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return PageResponse.of(postRepository.findAllByUserIdAndVisibilityOrderByPublishedAtDesc(
                user.getId(), PostVisibility.PUBLIC, pageable), this::toView);
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
        return toView(post);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostView> listPublicByBook(Long bookId, Pageable pageable) {
        return PageResponse.of(postRepository.findAllByBookIdAndVisibilityOrderByPublishedAtDesc(
                bookId, PostVisibility.PUBLIC, pageable), this::toView);
    }

    private Post owned(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!post.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        return post;
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

    private PostView toView(Post post) {
        Book book = post.getBookId() == null
                ? null
                : bookRepository.findById(post.getBookId()).orElse(null);
        User author = userRepository.findById(post.getUserId()).orElse(null);
        return new PostView(
                post.getId(), post.getSlug(), post.getTitle(), post.getBodyMd(),
                post.getVisibility(), Arrays.asList(post.getTags()),
                post.getBookId(),
                book == null ? null : book.getTitle(),
                book == null ? null : book.getCoverUrl(),
                author == null ? null : author.getHandle(),
                author == null ? null : author.getNickname(),
                post.getPublishedAt(), post.getViewCount());
    }
}
