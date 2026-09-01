package app.bookey.api.quote;

import app.bookey.api.quote.dto.QuoteDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.quote.BookQuote;
import app.bookey.domain.quote.BookQuoteRepository;
import app.bookey.domain.quote.QuoteAgree;
import app.bookey.domain.quote.QuoteAgreeRepository;
import app.bookey.domain.quote.QuoteAgreeRepository.AgreeCount;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 오려둔 문장(밑줄) — 작성 · 삭제 · 목록 · 나도 그럼(agree) 토글. */
@Service
@RequiredArgsConstructor
public class QuoteService {

    /** 도배 방지 — 1분에 10건. */
    private static final int CREATE_RATE_LIMIT = 10;

    private final BookQuoteRepository quoteRepository;
    private final QuoteAgreeRepository agreeRepository;
    private final BookRepository bookRepository;
    private final ReadingRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    @Transactional
    public BookQuoteView create(Long userId, CreateBookQuoteRequest request) {
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        if (request.readingRecordId() != null) {
            ReadingRecord record = recordRepository.findById(request.readingRecordId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
            if (!record.isOwnedBy(userId)) {
                throw ApiException.of(ErrorCode.FORBIDDEN);
            }
        }
        rateLimiter.require("quote:create:" + userId, CREATE_RATE_LIMIT, Duration.ofMinutes(1));

        BookQuote quote = quoteRepository.save(BookQuote.builder()
                .userId(userId)
                .bookId(book.getId())
                .readingRecordId(request.readingRecordId())
                .content(request.content())
                .page(request.page())
                .build());

        User author = userRepository.findById(userId).orElse(null);
        List<BookQuoteView> views = assembleViews(List.of(quote), userId,
                Map.of(book.getId(), book),
                author == null ? Map.of() : Map.of(userId, author),
                Map.of(), Set.of());
        return views.get(0);
    }

    /** 내가 오려둔 문장 목록 — 최신순. */
    @Transactional(readOnly = true)
    public PageResponse<BookQuoteView> myQuotes(Long userId, Pageable pageable) {
        Page<BookQuote> page = quoteRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return toPageResponse(page, userId);
    }

    /** 책별 오려둔 문장 목록 — 최신순. */
    @Transactional(readOnly = true)
    public PageResponse<BookQuoteView> byBook(Long userId, Long bookId, Pageable pageable) {
        bookRepository.findById(bookId).orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        Page<BookQuote> page = quoteRepository.findAllByBookIdOrderByCreatedAtDescIdDesc(bookId, pageable);
        return toPageResponse(page, userId);
    }

    @Transactional
    public void delete(Long userId, Long quoteId) {
        BookQuote quote = getQuote(quoteId);
        if (!quote.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        quoteRepository.delete(quote);
    }

    /** 나도 그럼 토글 — BookService.toggleLike 미러(find→delete/save, 카운트 반환). */
    @Transactional
    public QuoteAgreeView toggleAgree(Long userId, Long quoteId) {
        getQuote(quoteId);
        var existing = agreeRepository.findByUserIdAndQuoteId(userId, quoteId);
        boolean agreed;
        if (existing.isPresent()) {
            agreeRepository.delete(existing.get());
            agreed = false;
        } else {
            agreeRepository.save(QuoteAgree.builder().userId(userId).quoteId(quoteId).build());
            agreed = true;
        }
        return new QuoteAgreeView(agreed, agreeRepository.countByQuoteId(quoteId));
    }

    // ────────────────────────────── 내부 ──────────────────────────────

    private BookQuote getQuote(Long quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> ApiException.of(ErrorCode.QUOTE_NOT_FOUND));
    }

    private PageResponse<BookQuoteView> toPageResponse(Page<BookQuote> page, Long userId) {
        List<BookQuote> quotes = page.getContent();
        Map<Long, Book> books = loadBooks(quotes);
        Map<Long, User> authors = loadAuthors(quotes);
        Map<Long, Long> agreeCounts = loadAgreeCounts(quotes);
        Set<Long> myAgreed = loadMyAgreed(userId, quotes);
        List<BookQuoteView> views = assembleViews(quotes, userId, books, authors, agreeCounts, myAgreed);
        return new PageResponse<>(views, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    private Map<Long, Book> loadBooks(List<BookQuote> quotes) {
        List<Long> ids = quotes.stream().map(BookQuote::getBookId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bookRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
    }

    private Map<Long, User> loadAuthors(List<BookQuote> quotes) {
        List<Long> ids = quotes.stream().map(BookQuote::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Long> loadAgreeCounts(List<BookQuote> quotes) {
        List<Long> ids = quotes.stream().map(BookQuote::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return agreeRepository.countPerQuote(ids).stream()
                .collect(Collectors.toMap(AgreeCount::getQuoteId, AgreeCount::getAgreeCount));
    }

    private Set<Long> loadMyAgreed(Long userId, List<BookQuote> quotes) {
        List<Long> ids = quotes.stream().map(BookQuote::getId).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return agreeRepository.findAllByUserIdAndQuoteIdIn(userId, ids).stream()
                .map(QuoteAgree::getQuoteId)
                .collect(Collectors.toSet());
    }

    /**
     * 배치 맵으로 뷰를 조립한다(ClubPostService.loadAuthors·HomeContentAssembler 선례).
     * agreeCount는 결측 시 0, 작성자가 탈퇴했으면 "알 수 없음"으로 대체한다.
     */
    static List<BookQuoteView> assembleViews(List<BookQuote> quotes, Long viewerId,
                                             Map<Long, Book> books, Map<Long, User> authors,
                                             Map<Long, Long> agreeCounts, Set<Long> myAgreedQuoteIds) {
        return quotes.stream()
                .map(quote -> {
                    Book book = books.get(quote.getBookId());
                    User author = authors.get(quote.getUserId());
                    return new BookQuoteView(
                            quote.getId(),
                            quote.getBookId(),
                            book == null ? null : book.getTitle(),
                            book == null ? null : book.getCoverUrl(),
                            quote.getPage(),
                            quote.getContent(),
                            quote.getUserId(),
                            author == null ? "알 수 없음" : author.getNickname(),
                            author == null ? null : author.getAvatarUrl(),
                            agreeCounts.getOrDefault(quote.getId(), 0L),
                            myAgreedQuoteIds.contains(quote.getId()),
                            quote.isOwnedBy(viewerId),
                            quote.getCreatedAt() == null ? Instant.now() : quote.getCreatedAt());
                })
                .toList();
    }
}
