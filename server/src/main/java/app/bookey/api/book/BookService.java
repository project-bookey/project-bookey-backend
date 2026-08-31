package app.bookey.api.book;

import app.bookey.api.book.dto.BookDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.*;
import app.bookey.domain.curation.EditorPick;
import app.bookey.domain.curation.EditorPickRepository;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingRecordRepository.BookSavedCount;
import app.bookey.domain.review.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookService {

    /** 크라우드 페이지 수 채택 기준 — 동일 값 3표. */
    private static final int CROWD_ADOPT_VOTES = 3;

    private final BookRepository bookRepository;
    private final BookPageSuggestionRepository suggestionRepository;
    private final ReviewRepository reviewRepository;
    private final BookSearchService searchService;
    private final ReadingRecordRepository readingRecordRepository;
    private final EditorPickRepository editorPickRepository;

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

    @Transactional(readOnly = true)
    public BookDetail detail(Long bookId) {
        Book book = getBook(bookId);
        return new BookDetail(
                BookSummary.from(book),
                book.getDescription(),
                toRating(reviewRepository.verifiedRating(bookId)),
                toRating(reviewRepository.overallRating(bookId)),
                toRating(reviewRepository.verifiedRating(bookId)).count());
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

    /** 추천 도서 — 에디터 픽 순서를 따른다. */
    @Transactional(readOnly = true)
    public List<BookSummary> recommended(int size) {
        List<EditorPick> picks = editorPickRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Book> books = bookRepository.findAllById(
                        picks.stream().map(EditorPick::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, b -> b));
        return assembleRecommended(picks.stream().limit(size).toList(), books);
    }

    static List<BookSummary> assembleRecommended(List<EditorPick> picks, Map<Long, Book> books) {
        return picks.stream()
                .filter(p -> books.containsKey(p.getBookId()))
                .map(p -> BookSummary.from(books.get(p.getBookId())))
                .toList();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
