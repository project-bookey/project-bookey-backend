package app.bookey.api.book;

import app.bookey.api.book.dto.BookDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.*;
import app.bookey.domain.curation.EditorPick;
import app.bookey.domain.curation.EditorPickRepository;
import app.bookey.domain.like.BookLike;
import app.bookey.domain.like.BookLikeRepository;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingRecordRepository.BookSavedCount;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookService {

    /** 크라우드 페이지 수 채택 기준 — 동일 값 3표. */
    private static final int CROWD_ADOPT_VOTES = 3;
    private static final int ONBOARDING_CATEGORY_LIMIT = 24;
    private static final List<String> DEFAULT_ONBOARDING_CATEGORIES = List.of(
            "소설", "에세이", "시", "인문학", "역사", "과학",
            "자기계발", "경제/경영", "컴퓨터/IT", "예술", "여행", "만화");

    private final BookRepository bookRepository;
    private final app.bookey.api.book.client.Yes24Client yes24Client;
    private final BookPageSuggestionRepository suggestionRepository;
    private final ReviewRepository reviewRepository;
    private final BookSearchService searchService;
    private final ReadingRecordRepository readingRecordRepository;
    private final EditorPickRepository editorPickRepository;
    private final BookLikeRepository bookLikeRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<BookSummary> search(String keyword, int size) {
        return searchService.search(keyword, size).stream()
                .map(BookSummary::from)
                .toList();
    }

    @Transactional
    public BookSummary findByIsbn(String isbn13) {
        return searchService.findOrFetchByIsbn(isbn13)
                .map(BookSummary::from)
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
    }

    /** 쓰기 트랜잭션인 이유: 상세를 처음 열 때 YES24 링크·목차를 게으르게 채워 넣는다. */
    @Transactional
    public BookDetail detail(Long userId, Long bookId) {
        Book book = getBook(bookId);
        enrichYes24IfMissing(book);
        Long myRecordId = userId == null ? null
                : readingRecordRepository.findFirstByUserIdAndBookIdOrderByRoundDesc(userId, bookId)
                        .map(ReadingRecord::getId)
                        .orElse(null);
        return new BookDetail(
                BookSummary.from(book),
                book.getDescription(),
                toRating(reviewRepository.verifiedRating(bookId)),
                toRating(reviewRepository.overallRating(bookId)),
                toRating(reviewRepository.verifiedRating(bookId)).count(),
                bookLikeRepository.existsByUserIdAndBookId(userId, bookId),
                bookLikeRepository.countByBookId(bookId),
                myRecordId,
                book.getPurchaseLink(),
                book.getAddonLink(),
                book.getTableOfContents());
    }

    /** YES24 부가 정보(구매 링크·목차)가 없으면 상세 조회 시 한 번 채운다 — 실패해도 상세는 그대로 나간다. */
    private void enrichYes24IfMissing(Book book) {
        if (book.getAddonLink() != null || book.getTableOfContents() != null
                || book.getIsbn13() == null || !yes24Client.isConfigured()) {
            return;
        }
        yes24Client.detailByIsbn13(book.getIsbn13()).ifPresent(item ->
                book.applyYes24(item.purchaseLink(), item.addonLink(),
                        item.tableOfContents(), item.introduction()));
    }

    /** 좋아요 토글 — 있으면 해제, 없으면 등록. */
    @Transactional
    public BookLikeView toggleLike(Long userId, Long bookId) {
        getBook(bookId);
        var existing = bookLikeRepository.findByUserIdAndBookId(userId, bookId);
        boolean liked;
        if (existing.isPresent()) {
            bookLikeRepository.delete(existing.get());
            liked = false;
        } else {
            bookLikeRepository.save(BookLike.builder().userId(userId).bookId(bookId).build());
            liked = true;
        }
        return new BookLikeView(liked, bookLikeRepository.countByBookId(bookId));
    }

    private RatingSummary toRating(List<Object[]> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return RatingSummary.empty();
        }
        Object[] row = rows.get(0);
        Double avg = (Double) row[0];
        Long count = (Long) row[1];
        return new RatingSummary(avg, count == null ? 0 : count);
    }

    /** 외부 API 에 없는 책 수동 등록 (§F1). */
    @Transactional
    public BookSummary createManual(ManualBookRequest request) {
        if (request.isbn13() != null && !request.isbn13().isBlank()) {
            var existing = bookRepository.findByIsbn13(request.isbn13());
            if (existing.isPresent()) {
                return BookSummary.from(existing.get());
            }
        }
        Book book = Book.builder()
                .isbn13(emptyToNull(request.isbn13()))
                .title(request.title())
                .author(request.author())
                .publisher(request.publisher())
                .totalPages(request.totalPages())
                .coverUrl(request.coverUrl())
                .category(request.category())
                .publishedAt(request.publishedAt())
                .source(BookSource.MANUAL)
                .userCreated(true)
                .build();
        return BookSummary.from(bookRepository.save(book));
    }

    /**
     * 페이지 수 크라우드 입력 (§12 리스크 대응).
     * 같은 값이 3표 모이면 books.total_pages 로 채택한다.
     */
    @Transactional
    public PageSuggestionResponse suggestTotalPages(Long userId, Long bookId, int totalPages) {
        Book book = getBook(bookId);
        suggestionRepository.findByBookIdAndUserId(bookId, userId)
                .ifPresentOrElse(
                        s -> s.update(totalPages),
                        () -> suggestionRepository.save(
                                new BookPageSuggestion(bookId, userId, totalPages)));

        List<Object[]> tally = suggestionRepository.tallyVotes(bookId);
        if (tally.isEmpty()) {
            return new PageSuggestionResponse(book.getTotalPages(), 0, false);
        }
        Object[] top = tally.get(0);
        int pages = ((Number) top[0]).intValue();
        int votes = ((Number) top[1]).intValue();

        boolean applied = false;
        if (!book.hasTotalPages() && votes >= CROWD_ADOPT_VOTES) {
            book.applyCrowdPages(pages);
            applied = true;
        }
        return new PageSuggestionResponse(book.getTotalPages(), votes, applied);
    }

    @Transactional(readOnly = true)
    public Book getBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
    }

    /** 인기 도서 — 서재에 담긴 수(중복 사용자 제거) 순. */
    @Transactional(readOnly = true)
    public List<PopularBookView> popular(int size) {
        List<BookSavedCount> counts = readingRecordRepository.countSavedPerBook(PageRequest.of(0, size));
        Map<Long, Book> books = bookRepository.findAllById(
                        counts.stream().map(BookSavedCount::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, b -> b));
        return assemblePopular(counts, books);
    }

    static List<PopularBookView> assemblePopular(List<BookSavedCount> counts, Map<Long, Book> books) {
        return counts.stream()
                .filter(c -> books.containsKey(c.getBookId()))
                .map(c -> new PopularBookView(BookSummary.from(books.get(c.getBookId())), c.getSavedCount()))
                .toList();
    }

    /** 온보딩 책 고르기 (비회원) — 표지 있는 책만, 카테고리 부분 일치. */
    @Transactional(readOnly = true)
    public List<BookSummary> onboardingPicks(String category, int size) {
        String normalized = category == null || category.isBlank() ? null : category.trim();
        return bookRepository.findOnboardingPicks(normalized, PageRequest.of(0, size)).stream()
                .map(BookSummary::from)
                .toList();
    }

    /** 온보딩 선호 카테고리 — 책 메타에서 가져오고, 초기 데이터가 적으면 기본값으로 보강한다. */
    @Transactional(readOnly = true)
    public List<String> onboardingCategories() {
        java.util.LinkedHashSet<String> categories = new java.util.LinkedHashSet<>();
        bookRepository.findDistinctCategories(PageRequest.of(0, 200)).stream()
                .flatMap(raw -> toOnboardingCategories(raw).stream())
                .forEach(categories::add);
        DEFAULT_ONBOARDING_CATEGORIES.forEach(categories::add);
        return categories.stream().limit(ONBOARDING_CATEGORY_LIMIT).toList();
    }

    private static List<String> toOnboardingCategories(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> matches = DEFAULT_ONBOARDING_CATEGORIES.stream()
                .filter(raw::contains)
                .toList();
        if (!matches.isEmpty()) {
            return matches;
        }
        String[] parts = raw.split("[>/]");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isBlank() && part.length() <= 20) {
                return List.of(part);
            }
        }
        return List.of();
    }

    /** 추천 도서 — 에디터 픽 → 선호 카테고리 → 최신 내부 책 → YES24 베스트셀러 순으로 보강한다. */
    @Transactional
    public List<BookSummary> recommended(Long userId, int size) {
        int capped = Math.min(Math.max(size, 1), 50);
        Set<Long> savedBookIds = userId == null
                ? Set.of()
                : Set.copyOf(readingRecordRepository.findDistinctBookIdsByUserId(userId));
        List<Long> excludedBookIds = savedBookIds.isEmpty() ? List.of(-1L) : savedBookIds.stream().toList();
        LinkedHashMap<Long, Book> result = new LinkedHashMap<>();

        List<EditorPick> picks = editorPickRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Book> pickedBooks = bookRepository.findAllById(
                        picks.stream().map(EditorPick::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, b -> b));
        assembleRecommended(picks, pickedBooks).forEach(book -> putRecommended(result, book, savedBookIds, capped));

        if (result.size() < capped) {
            preferredCategories(userId).forEach(category ->
                    bookRepository.findRecommendationsByCategoryExcluding(
                                    category, excludedBookIds, PageRequest.of(0, capped))
                            .forEach(book -> putRecommended(result, book, savedBookIds, capped)));
        }

        if (result.size() < capped) {
            bookRepository.findRecommendationsExcluding(excludedBookIds, PageRequest.of(0, capped))
                    .forEach(book -> putRecommended(result, book, savedBookIds, capped));
        }

        if (result.size() < capped && yes24Client.isConfigured()) {
            List<Book> yes24Books = searchService.upsertYes24(
                    yes24Client.curation(app.bookey.api.book.client.Yes24Client.CurationKind.BESTSELLER, capped));
            yes24Books.forEach(book -> putRecommended(result, book, savedBookIds, capped));
        }

        return result.values().stream().map(BookSummary::from).toList();
    }

    static List<Book> assembleRecommended(List<EditorPick> picks, Map<Long, Book> books) {
        return picks.stream()
                .filter(p -> books.containsKey(p.getBookId()))
                .map(p -> books.get(p.getBookId()))
                .toList();
    }

    private List<String> preferredCategories(Long userId) {
        if (userId == null) {
            return DEFAULT_ONBOARDING_CATEGORIES;
        }
        return userRepository.findById(userId)
                .map(user -> Arrays.stream(user.getPreferredCategories())
                        .filter(category -> category != null && !category.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList())
                .filter(categories -> !categories.isEmpty())
                .orElse(DEFAULT_ONBOARDING_CATEGORIES);
    }

    private static void putRecommended(LinkedHashMap<Long, Book> result, Book book, Set<Long> savedBookIds, int limit) {
        if (result.size() >= limit || book == null || book.getId() == null || savedBookIds.contains(book.getId())) {
            return;
        }
        result.putIfAbsent(book.getId(), book);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
