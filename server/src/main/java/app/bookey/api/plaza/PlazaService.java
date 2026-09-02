package app.bookey.api.plaza;

import app.bookey.api.plaza.dto.PlazaDtos.PlazaItemView;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.quote.BookQuote;
import app.bookey.domain.quote.BookQuoteRepository;
import app.bookey.domain.quote.QuoteAgree;
import app.bookey.domain.quote.QuoteAgreeRepository;
import app.bookey.domain.quote.QuoteAgreeRepository.AgreeCount;
import app.bookey.domain.quote.QuoteCommentRepository;
import app.bookey.domain.quote.QuoteCommentRepository.CommentCount;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 광장(플라자) — 전체 사용자 공개 피드(밑줄 QUOTE · 완독 자랑 FINISH). */
@Service
@RequiredArgsConstructor
public class PlazaService {

    private final BookQuoteRepository quoteRepository;
    private final QuoteAgreeRepository agreeRepository;
    private final QuoteCommentRepository commentRepository;
    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<PlazaItemView> feed(Long userId, PlazaItemType type, Pageable pageable) {
        return type == PlazaItemType.FINISH ? finishFeed(pageable) : quoteFeed(userId, pageable);
    }

    private PageResponse<PlazaItemView> quoteFeed(Long userId, Pageable pageable) {
        Page<BookQuote> page = quoteRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
        List<BookQuote> quotes = page.getContent();
        Map<Long, Book> books = loadBooks(quotes.stream().map(BookQuote::getBookId).distinct().toList());
        Map<Long, User> authors = loadAuthors(quotes.stream().map(BookQuote::getUserId).distinct().toList());
        Map<Long, Long> agreeCounts = loadAgreeCounts(quotes);
        Set<Long> myAgreed = loadMyAgreed(userId, quotes);
        Map<Long, Long> commentCounts = loadCommentCounts(quotes);
        List<PlazaItemView> items = assembleQuoteItems(quotes, books, authors, agreeCounts, myAgreed, commentCounts);
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    private PageResponse<PlazaItemView> finishFeed(Pageable pageable) {
        Page<ReadingRecord> page = recordRepository.findFinishFeed(pageable);
        List<ReadingRecord> records = page.getContent();
        Map<Long, Book> books = loadBooks(records.stream().map(ReadingRecord::getBookId).distinct().toList());
        Map<Long, User> authors = loadAuthors(records.stream().map(ReadingRecord::getUserId).distinct().toList());
        List<PlazaItemView> items = assembleFinishItems(records, books, authors);
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    // ────────────────────────────── 내부 ──────────────────────────────

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

    private Map<Long, Long> loadCommentCounts(List<BookQuote> quotes) {
        List<Long> ids = quotes.stream().map(BookQuote::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countPerQuote(ids).stream()
                .collect(Collectors.toMap(CommentCount::getQuoteId, CommentCount::getCommentCount));
    }

    /**
     * 밑줄(QUOTE) 아이템을 배치 맵으로 조립한다(QuoteService.assembleViews 선례).
     * agreeCount·commentCount 결측 0, 책이 결측된 행은 필터하고, 탈퇴한 작성자는 "알 수 없음"으로 대체한다.
     */
    static List<PlazaItemView> assembleQuoteItems(List<BookQuote> quotes, Map<Long, Book> books,
                                                  Map<Long, User> authors, Map<Long, Long> agreeCounts,
                                                  Set<Long> myAgreedQuoteIds, Map<Long, Long> commentCounts) {
        return quotes.stream()
                .filter(quote -> books.containsKey(quote.getBookId()))
                .map(quote -> {
                    Book book = books.get(quote.getBookId());
                    User author = authors.get(quote.getUserId());
                    return new PlazaItemView(
                            PlazaItemType.QUOTE,
                            quote.getUserId(),
                            author == null ? "알 수 없음" : author.getNickname(),
                            author == null ? null : author.getAvatarUrl(),
                            book.getId(),
                            book.getTitle(),
                            book.getCoverUrl(),
                            quote.getCreatedAt() == null ? Instant.now() : quote.getCreatedAt(),
                            quote.getId(),
                            quote.getContent(),
                            quote.getPage(),
                            agreeCounts.getOrDefault(quote.getId(), 0L),
                            myAgreedQuoteIds.contains(quote.getId()),
                            commentCounts.getOrDefault(quote.getId(), 0L));
                })
                .toList();
    }

    /**
     * 완독 자랑(FINISH) 아이템을 배치 맵으로 조립한다. QUOTE 전용 필드는 전부 null,
     * occurredAt은 finishedAt이다. 책이 결측된 행은 필터하고, 탈퇴한 작성자는 "알 수 없음"으로 대체한다.
     */
    static List<PlazaItemView> assembleFinishItems(List<ReadingRecord> records, Map<Long, Book> books,
                                                   Map<Long, User> authors) {
        return records.stream()
                .filter(record -> books.containsKey(record.getBookId()))
                .map(record -> {
                    Book book = books.get(record.getBookId());
                    User author = authors.get(record.getUserId());
                    return new PlazaItemView(
                            PlazaItemType.FINISH,
                            record.getUserId(),
                            author == null ? "알 수 없음" : author.getNickname(),
                            author == null ? null : author.getAvatarUrl(),
                            book.getId(),
                            book.getTitle(),
                            book.getCoverUrl(),
                            record.getFinishedAt(),
                            null, null, null, null, null, null);
                })
                .toList();
    }
}
