package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.PostImageView;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.api.quote.dto.QuoteDtos.BookQuoteView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookSource;
import app.bookey.domain.post.Post;
import app.bookey.domain.post.PostImage;
import app.bookey.domain.post.PostVisibility;
import app.bookey.domain.quote.BookQuote;
import app.bookey.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostServiceTest {

    private Post post(long id, long userId, Long bookId, String bodyMd) {
        Post post = Post.builder()
                .userId(userId).bookId(bookId).slug("slug-" + id).title("제목 " + id).bodyMd(bodyMd)
                .visibility(PostVisibility.PUBLIC).tags(new String[]{"소설", "여름"})
                .build();
        set(post, "id", id);
        return post;
    }

    private PostImage image(long id, long userId, String url) {
        PostImage image = PostImage.builder()
                .userId(userId).storageKey("key-" + id).url(url).contentType("image/jpeg")
                .byteSize(1024).width(800).height(600)
                .build();
        set(image, "id", id);
        return image;
    }

    private BookQuote quote(long id, long userId, long bookId) {
        BookQuote quote = BookQuote.builder()
                .userId(userId).bookId(bookId).content("문장 " + id).page(10)
                .build();
        set(quote, "id", id);
        return quote;
    }

    private BookQuoteView quoteView(long id) {
        return new BookQuoteView(id, 100L, "책", null, 10, "문장 " + id,
                10L, "작가", null, 0L, false, true, 0L, Instant.now());
    }

    // Book.builder()는 id(자동 생성 PK)를 받지 않는다 — QuoteServiceTest처럼 리플렉션으로 채운다.
    private Book book(long id, String title) {
        Book book = Book.builder().title(title).source(BookSource.MANUAL).coverUrl("cover-" + id).build();
        set(book, "id", id);
        return book;
    }

    private User user(long id, String nickname) {
        User user = User.builder().handle("handle" + id).nickname(nickname).avatarUrl("avatar-" + id).build();
        set(user, "id", id);
        return user;
    }

    /** id 는 엔티티 자신에, createdAt 은 BaseTimeEntity 에 있어 상위 클래스까지 올라가며 찾는다. */
    private void set(Object target, String field, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ────────────────────────────── assembleViews ──────────────────────────────

    @Test
    @DisplayName("작성자·책·사진·밑줄·좋아요·댓글을 배치 맵으로 매핑한다 — 조회자가 작성자면 mine=true")
    void mapsAllFieldsFromBatchMaps() {
        Post post = post(1L, 10L, 100L, "# 첫 독후감\n\n본문 **강조** [링크](https://x)");
        Instant createdAt = Instant.parse("2026-09-01T00:00:00Z");
        set(post, "createdAt", createdAt);
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));
        Map<Long, List<PostImage>> images = Map.of(1L, List.of(image(7L, 10L, "u7"), image(3L, 10L, "u3")));
        Map<Long, List<BookQuoteView>> quotes = Map.of(1L, List.of(quoteView(5L), quoteView(2L)));

        List<PostView> views = PostService.assembleViews(List.of(post), 10L, books, authors, images, quotes,
                Map.of(1L, 3L), Set.of(1L), Map.of(1L, 4L));

        PostView view = views.get(0);
        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.slug()).isEqualTo("slug-1");
        assertThat(view.title()).isEqualTo("제목 1");
        assertThat(view.bodyMd()).startsWith("# 첫 독후감");
        assertThat(view.visibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(view.tags()).containsExactly("소설", "여름");
        assertThat(view.bookId()).isEqualTo(100L);
        assertThat(view.bookTitle()).isEqualTo("책");
        assertThat(view.bookCoverUrl()).isEqualTo("cover-100");
        assertThat(view.authorId()).isEqualTo(10L);
        assertThat(view.authorHandle()).isEqualTo("handle10");
        assertThat(view.authorNickname()).isEqualTo("작가");
        assertThat(view.authorAvatarUrl()).isEqualTo("avatar-10");
        assertThat(view.publishedAt()).isNotNull(); // PUBLIC 으로 만들었으므로 발행 시각이 있다
        assertThat(view.excerpt()).isEqualTo("첫 독후감 본문 강조 링크");
        assertThat(view.images()).extracting(PostImageView::id).containsExactly(7L, 3L);
        assertThat(view.images()).extracting(PostImageView::url).containsExactly("u7", "u3");
        assertThat(view.images().get(0).width()).isEqualTo(800);
        assertThat(view.images().get(0).height()).isEqualTo(600);
        assertThat(view.quotes()).extracting(BookQuoteView::id).containsExactly(5L, 2L);
        assertThat(view.likeCount()).isEqualTo(3L);
        assertThat(view.likedByMe()).isTrue();
        assertThat(view.commentCount()).isEqualTo(4L);
        assertThat(view.mine()).isTrue();
        assertThat(view.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("좋아요·댓글 수가 없으면 0, 사진·밑줄이 없으면 빈 목록이다 — 다른 조회자는 mine=false")
    void missingCountsDefaultToZeroAndEmptyLists() {
        Post post = post(2L, 10L, 100L, "본문");
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PostView> views = PostService.assembleViews(List.of(post), 20L, books, authors,
                Map.of(), Map.of(), Map.of(), Set.of(), Map.of());

        PostView view = views.get(0);
        assertThat(view.likeCount()).isZero();
        assertThat(view.likedByMe()).isFalse();
        assertThat(view.commentCount()).isZero();
        assertThat(view.images()).isEmpty();
        assertThat(view.quotes()).isEmpty();
        assertThat(view.mine()).isFalse();
        assertThat(view.createdAt()).isNotNull(); // 저장 전이라 createdAt 이 없어도 now 로 채운다
    }

    @Test
    @DisplayName("탈퇴한 작성자는 '알 수 없음'으로 대체하고 핸들·아바타는 비운다")
    void withdrawnAuthorFallsBackToUnknown() {
        Post post = post(3L, 99L, 100L, "본문");
        Map<Long, Book> books = Map.of(100L, book(100L, "책"));

        List<PostView> views = PostService.assembleViews(List.of(post), 1L, books, Map.of(),
                Map.of(), Map.of(), Map.of(), Set.of(), Map.of());

        PostView view = views.get(0);
        assertThat(view.authorId()).isEqualTo(99L);
        assertThat(view.authorNickname()).isEqualTo("알 수 없음");
        assertThat(view.authorHandle()).isNull();
        assertThat(view.authorAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("책이 결측이면 제목·표지만 null 이고 bookId 는 그대로, 책 없는 글은 bookId 도 null 이다")
    void missingBookLeavesTitleAndCoverNull() {
        Post withMissingBook = post(4L, 10L, 100L, "본문");
        Post withoutBook = post(5L, 10L, null, "본문");
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PostView> views = PostService.assembleViews(List.of(withMissingBook, withoutBook), 10L,
                Map.of(), authors, Map.of(), Map.of(), Map.of(), Set.of(), Map.of());

        assertThat(views.get(0).bookId()).isEqualTo(100L);
        assertThat(views.get(0).bookTitle()).isNull();
        assertThat(views.get(0).bookCoverUrl()).isNull();
        assertThat(views.get(1).bookId()).isNull();
        assertThat(views.get(1).bookTitle()).isNull();
    }

    @Test
    @DisplayName("excerpt 는 본문을 한 줄로 접어 140자에서 말줄임한다")
    void excerptIsTruncatedTo140Chars() {
        Post post = post(6L, 10L, 100L, "가".repeat(200));

        List<PostView> views = PostService.assembleViews(List.of(post), 10L, Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Set.of(), Map.of());

        assertThat(views.get(0).excerpt()).hasSize(141).startsWith("가".repeat(140)).endsWith("…");
    }

    @Test
    @DisplayName("입력 순서를 그대로 유지한다")
    void preservesInputOrder() {
        List<Post> posts = List.of(post(5L, 10L, 100L, "a"), post(3L, 10L, 100L, "b"), post(9L, 10L, 100L, "c"));
        Map<Long, User> authors = Map.of(10L, user(10L, "작가"));

        List<PostView> views = PostService.assembleViews(posts, 10L, Map.of(), authors,
                Map.of(), Map.of(), Map.of(), Set.of(), Map.of());

        assertThat(views).extracting(PostView::id).containsExactly(5L, 3L, 9L);
    }

    @Test
    @DisplayName("비로그인 조회자(null)는 mine·likedByMe 가 모두 false 다")
    void anonymousViewerIsNeverOwnerNorLiker() {
        Post post = post(7L, 10L, 100L, "본문");

        List<PostView> views = PostService.assembleViews(List.of(post), null, Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(7L, 2L), Set.of(), Map.of());

        assertThat(views.get(0).mine()).isFalse();
        assertThat(views.get(0).likedByMe()).isFalse();
        assertThat(views.get(0).likeCount()).isEqualTo(2L);
    }

    // ────────────────────────────── validateQuoteAttachments ──────────────────────────────

    @Test
    @DisplayName("남의 밑줄은 붙일 수 없다 — INVALID_REQUEST 와 안내 메시지")
    void rejectsQuotesOwnedByOthers() {
        List<BookQuote> found = List.of(quote(1L, 10L, 100L), quote(2L, 99L, 100L));

        assertThatThrownBy(() -> PostService.validateQuoteAttachments(10L, List.of(1L, 2L), found))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("내가 오려둔 밑줄만 붙일 수 있습니다.");
                });
    }

    @Test
    @DisplayName("없는 밑줄 id 가 섞여 개수가 맞지 않으면 거부한다")
    void rejectsWhenSomeQuotesAreMissing() {
        List<BookQuote> found = List.of(quote(1L, 10L, 100L));

        assertThatThrownBy(() -> PostService.validateQuoteAttachments(10L, List.of(1L, 404L), found))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("다른 책의 밑줄도 내 것이면 허용한다 — 책 정보는 아예 보지 않는다")
    void allowsOwnQuotesRegardlessOfBook() {
        List<BookQuote> found = List.of(quote(1L, 10L, 100L), quote(2L, 10L, 200L));

        assertThatCode(() -> PostService.validateQuoteAttachments(10L, List.of(1L, 2L), found))
                .doesNotThrowAnyException();
    }

    // ────────────────────────────── slugify ──────────────────────────────

    @Test
    @DisplayName("slugify — 소문자로 바꾸고 기호는 하이픈으로, 한글은 그대로 둔다")
    void slugifyNormalizesTitle() {
        assertThat(PostService.slugify("Hello, World!")).isEqualTo("hello-world");
        assertThat(PostService.slugify("오늘의 독서 — 2회차")).isEqualTo("오늘의-독서-2회차");
    }

    @Test
    @DisplayName("slugify — 남는 글자가 없으면 post, 80자를 넘으면 자른다")
    void slugifyFallsBackAndTruncates() {
        assertThat(PostService.slugify("!!!")).isEqualTo("post");
        assertThat(PostService.slugify("a".repeat(100))).hasSize(80);
    }
}
