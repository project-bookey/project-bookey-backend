package app.bookey.api.library;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.api.library.dto.LibraryDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.reading.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final ProgressService progressService;

    @Transactional
    public ReadingRecordView addBook(Long userId, AddBookRequest request) {
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));

        List<ReadingRecord> existing =
                recordRepository.findAllByUserIdAndBookIdOrderByRoundDesc(userId, book.getId());

        boolean reread = Boolean.TRUE.equals(request.reread());
        if (!existing.isEmpty() && !reread) {
            ReadingRecord latest = existing.get(0);
            if (!latest.getStatus().isClosed()) {
                throw ApiException.of(ErrorCode.ALREADY_IN_LIBRARY);
            }
        }
        // 이미 완독/하차한 책을 다시 담으면 새 회차로 시작한다(§F2 재독).
        short nextRound = existing.isEmpty() ? 1 : (short) (existing.get(0).getRound() + 1);

        ReadingRecord record = ReadingRecord.builder()
                .userId(userId)
                .bookId(book.getId())
                .round(nextRound)
                .status(request.status() == null ? ReadingStatus.WANT_TO_READ : request.status())
                .targetFinishDate(request.targetFinishDate())
                .totalPagesOverride(request.totalPagesOverride())
                .build();
        return toView(recordRepository.save(record), book);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadingRecordView> list(Long userId, ReadingStatus status, Pageable pageable) {
        Page<ReadingRecord> page = status == null
                ? recordRepository.findLibrary(userId, pageable)
                : recordRepository.findLibraryByStatus(userId, status, pageable);
        Map<Long, Book> books = loadBooks(page.getContent());
        return PageResponse.of(page, record -> toView(record, books.get(record.getBookId())));
    }

    @Transactional(readOnly = true)
    public LibrarySummary summary(Long userId) {
        return new LibrarySummary(
                recordRepository.countByUserIdAndStatus(userId, ReadingStatus.READING),
                recordRepository.countByUserIdAndStatus(userId, ReadingStatus.WANT_TO_READ),
                recordRepository.countByUserIdAndStatus(userId, ReadingStatus.FINISHED),
                recordRepository.countByUserIdAndStatus(userId, ReadingStatus.ABANDONED),
                recordRepository.countByUserIdAndStatus(userId, ReadingStatus.PAUSED));
    }

    @Transactional(readOnly = true)
    public ReadingRecordView detail(Long userId, Long recordId) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        return toView(record, bookRepository.findById(record.getBookId()).orElse(null));
    }

    @Transactional
    public ReadingRecordView updateGoal(Long userId, Long recordId, UpdateGoalRequest request) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        if (request.targetFinishDate() != null) {
            record.changeTargetDate(request.targetFinishDate());
        }
        if (request.totalPagesOverride() != null) {
            record.overrideTotalPages(request.totalPagesOverride());
        }
        return toView(record, bookRepository.findById(record.getBookId()).orElse(null));
    }

    @Transactional
    public ReadingRecordView updateProgress(Long userId, Long recordId, int currentPage) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        Integer totalPages = book == null ? null : record.effectiveTotalPages(book.getTotalPages());
        record.setCurrentPage(currentPage, totalPages == null || totalPages == 0 ? null : totalPages);
        record.startReading(Instant.now());
        return toView(record, book);
    }

    @Transactional
    public ReadingRecordView pause(Long userId, Long recordId) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        record.pause();
        return toView(record, bookRepository.findById(record.getBookId()).orElse(null));
    }

    @Transactional
    public ReadingRecordView resume(Long userId, Long recordId) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        record.startReading(Instant.now());
        return toView(record, bookRepository.findById(record.getBookId()).orElse(null));
    }

    @Transactional
    public ReadingRecordView finish(Long userId, Long recordId, FinishRequest request) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        int totalPages = record.effectiveTotalPages(book == null ? null : book.getTotalPages());
        record.finish(Instant.now(), totalPages > 0 ? totalPages : null);
        if (request != null && request.rating() != null) {
            record.rate(request.rating());
        }
        return toView(record, book);
    }

    @Transactional
    public ReadingRecordView abandon(Long userId, Long recordId, AbandonRequest request) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        record.abandon(request.reason());
        return toView(record, bookRepository.findById(record.getBookId()).orElse(null));
    }

    @Transactional
    public void delete(Long userId, Long recordId) {
        ReadingRecord record = getOwnedRecord(userId, recordId);
        recordRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public ReadingRecord getOwnedRecord(Long userId, Long recordId) {
        ReadingRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
        if (!record.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        return record;
    }

    public ReadingRecordView toView(ReadingRecord record, Book book) {
        return new ReadingRecordView(
                record.getId(),
                record.getRound(),
                record.getStatus(),
                book == null ? null : BookSummary.from(book),
                progressService.toView(record, book),
                record.getTargetFinishDate(),
                record.getStartedAt(),
                record.getFinishedAt(),
                record.getLastReadAt(),
                record.getRating(),
                record.getAbandonReason() == null ? null : record.getAbandonReason().name());
    }

    private Map<Long, Book> loadBooks(List<ReadingRecord> records) {
        List<Long> bookIds = records.stream().map(ReadingRecord::getBookId).distinct().toList();
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
    }
}
