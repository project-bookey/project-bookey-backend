package app.bookey.api.challenge;

import app.bookey.api.book.dto.BookDtos.BookSummary;
import app.bookey.api.challenge.dto.ChallengeDtos.ChallengeProgressRequest;
import app.bookey.api.challenge.dto.ChallengeDtos.ChallengeView;
import app.bookey.api.challenge.dto.ChallengeDtos.CreateChallengeRequest;
import app.bookey.api.library.LibraryService;
import app.bookey.api.library.dto.LibraryDtos.AddBookRequest;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.challenge.ChallengeStatus;
import app.bookey.domain.challenge.ReadingChallenge;
import app.bookey.domain.challenge.ReadingChallengeRepository;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ReadingChallengeRepository challengeRepository;
    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final LibraryService libraryService;

    @Transactional
    public ChallengeView create(Long userId, CreateChallengeRequest req) {
        ReadingRecord record = resolveRecord(userId, req);
        if (record.getStatus() != ReadingStatus.READING) {
            throw ApiException.of(ErrorCode.CHALLENGE_INVALID_RECORD);
        }
        if (totalPages(record) <= 0) {
            throw ApiException.of(ErrorCode.CHALLENGE_REQUIRES_PAGES);
        }
        if (challengeRepository.existsByReadingRecordIdAndStatus(record.getId(), ChallengeStatus.ACTIVE)) {
            throw ApiException.of(ErrorCode.CHALLENGE_ALREADY_ACTIVE);
        }
        Instant now = Instant.now();
        ReadingChallenge challenge = ReadingChallenge.builder()
                .userId(userId).readingRecordId(record.getId()).budgetSec(req.budgetSec())
                .build();
        challenge.resume(now); // 생성 즉시 시작
        return view(challengeRepository.save(challenge), record, now);
    }

    @Transactional
    public List<ChallengeView> active(Long userId) {
        Instant now = Instant.now();
        return challengeRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, ChallengeStatus.ACTIVE)
                .stream()
                .map(c -> { lazyExpire(c, now); return c; })
                .filter(c -> c.getStatus() == ChallengeStatus.ACTIVE)
                .map(c -> view(c, ownedRecord(userId, c.getReadingRecordId()), now))
                .toList();
    }

    @Transactional
    public ChallengeView get(Long userId, Long id) {
        Instant now = Instant.now();
        ReadingChallenge c = owned(userId, id);
        lazyExpire(c, now);
        return view(c, ownedRecord(userId, c.getReadingRecordId()), now);
    }

    @Transactional
    public ChallengeView resume(Long userId, Long id) {
        return transition(userId, id, (c, now) -> c.resume(now));
    }

    @Transactional
    public ChallengeView pause(Long userId, Long id) {
        return transition(userId, id, (c, now) -> c.pause(now));
    }

    @Transactional
    public ChallengeView progress(Long userId, Long id, ChallengeProgressRequest req) {
        Instant now = Instant.now();
        ReadingChallenge c = owned(userId, id);
        lazyExpire(c, now);
        if (c.getStatus() != ChallengeStatus.ACTIVE) {
            throw ApiException.of(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }
        // 총쪽수 초과 입력은 총쪽수로 클램프 — 스펙: 도달이면 성공
        ReadingRecord record = ownedRecord(userId, c.getReadingRecordId());
        int total = totalPages(record);
        int page = Math.min(req.currentPage(), total);
        // 서재 진도 갱신 재사용
        libraryService.updateProgress(userId, c.getReadingRecordId(), page);
        if (page >= total) {
            c.succeed(now);
            libraryService.finish(userId, c.getReadingRecordId(), null); // 완독 처리 재사용
            record = ownedRecord(userId, c.getReadingRecordId());
        }
        return view(c, record, now);
    }

    @Transactional
    public void cancel(Long userId, Long id) {
        Instant now = Instant.now();
        ReadingChallenge c = owned(userId, id);
        lazyExpire(c, now);
        if (c.getStatus() != ChallengeStatus.ACTIVE) {
            throw ApiException.of(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }
        c.cancel(now);
    }

    // ── 내부 ─────────────────────────────────────────────
    private interface Transition { void apply(ReadingChallenge c, Instant now); }

    private ChallengeView transition(Long userId, Long id, Transition t) {
        Instant now = Instant.now();
        ReadingChallenge c = owned(userId, id);
        lazyExpire(c, now);
        if (c.getStatus() != ChallengeStatus.ACTIVE) {
            throw ApiException.of(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }
        t.apply(c, now);
        return view(c, ownedRecord(userId, c.getReadingRecordId()), now);
    }

    /** ACTIVE인데 예산을 다 썼으면 실패로 확정한다 (지연 만료). */
    private void lazyExpire(ReadingChallenge c, Instant now) {
        if (c.getStatus() == ChallengeStatus.ACTIVE && c.isExpired(now)) {
            c.fail(now);
        }
    }

    private ReadingChallenge owned(Long userId, Long id) {
        return challengeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    private ReadingRecord ownedRecord(Long userId, Long recordId) {
        return recordRepository.findById(recordId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
    }

    /** readingRecordId 또는 bookId 중 하나로 대상 기록을 해석한다 — 둘 다 없으면 거부. */
    private ReadingRecord resolveRecord(Long userId, CreateChallengeRequest req) {
        if (req.readingRecordId() != null) {
            return ownedRecord(userId, req.readingRecordId());
        }
        if (req.bookId() != null) {
            return resolveByBook(userId, req.bookId());
        }
        throw ApiException.of(ErrorCode.CHALLENGE_INVALID_RECORD);
    }

    /** bookId로 내 최신 기록을 찾아 READING으로 전환하거나, 없으면 서재에 새로 담는다. */
    private ReadingRecord resolveByBook(Long userId, Long bookId) {
        return recordRepository.findFirstByUserIdAndBookIdOrderByRoundDesc(userId, bookId)
                .map(this::startOrValidate)
                .orElseGet(() -> addToLibraryReading(userId, bookId));
    }

    private ReadingRecord startOrValidate(ReadingRecord record) {
        switch (record.getStatus()) {
            case READING -> { }
            case WANT_TO_READ, PAUSED -> record.startReading(Instant.now());
            case FINISHED, ABANDONED -> throw ApiException.of(ErrorCode.CHALLENGE_INVALID_RECORD);
        }
        return record;
    }

    private ReadingRecord addToLibraryReading(Long userId, Long bookId) {
        libraryService.addBook(userId, new AddBookRequest(bookId, ReadingStatus.READING, null, null, null));
        return recordRepository.findFirstByUserIdAndBookIdOrderByRoundDesc(userId, bookId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
    }

    private int totalPages(ReadingRecord record) {
        if (record.getTotalPagesOverride() != null && record.getTotalPagesOverride() > 0) {
            return record.getTotalPagesOverride();
        }
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        return book != null && book.getTotalPages() != null ? book.getTotalPages() : 0;
    }

    private ChallengeView view(ReadingChallenge c, ReadingRecord record, Instant now) {
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        BookSummary summary = book != null ? BookSummary.from(book) : null;
        return ChallengeView.of(c, summary, record.getCurrentPage(), totalPages(record), now);
    }
}
