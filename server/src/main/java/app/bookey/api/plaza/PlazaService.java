package app.bookey.api.plaza;

import app.bookey.api.plaza.dto.PlazaDtos.PlazaItemView;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 광장(플라자) — 전체 사용자 공개 피드(완독 자랑 FINISH). 밑줄(QUOTE)은 걷어내 늘 빈 페이지다. */
@Service
@RequiredArgsConstructor
public class PlazaService {

    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    /** 광장 피드 — 완독 자랑만 있다. 옛 앱이 밑줄(QUOTE)을 달라고 하면 빈 페이지를 돌려준다. */
    @Transactional(readOnly = true)
    public PageResponse<PlazaItemView> feed(PlazaItemType type, Pageable pageable) {
        if (type == PlazaItemType.QUOTE) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, false);
        }
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

    /**
     * 완독 자랑(FINISH) 아이템을 배치 맵으로 조립한다. occurredAt 은 finishedAt 이다.
     * 책이 결측된 행은 필터하고, 탈퇴한 작성자는 "알 수 없음"으로 대체한다.
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
                            record.getFinishedAt());
                })
                .toList();
    }
}
