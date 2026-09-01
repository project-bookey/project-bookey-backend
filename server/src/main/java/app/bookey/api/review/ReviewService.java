package app.bookey.api.review;

import app.bookey.api.review.dto.ReviewDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.admin.ModerationTicket;
import app.bookey.domain.admin.ModerationTicketRepository;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingStatus;
import app.bookey.domain.report.AbuseReport;
import app.bookey.domain.report.AbuseReportRepository;
import app.bookey.domain.review.Review;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 검증 리뷰 (§F6). */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AbuseReportRepository abuseReportRepository;
    private final ModerationTicketRepository moderationTicketRepository;
    private final VerificationService verificationService;

    @Transactional(readOnly = true)
    public VerificationPreview preview(Long userId, Long readingRecordId) {
        ReadingRecord record = ownedRecord(userId, readingRecordId);
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        var result = verificationService.evaluate(record, book);
        return new VerificationPreview(result.level(), result.coverage(), result.timerSessionCount(),
                result.verifiedMinutes(), result.requiredMinutes(), result.flags(),
                record.getStatus() == ReadingStatus.FINISHED);
    }

    @Transactional
    public ReviewView create(Long userId, CreateReviewRequest request) {
        ReadingRecord record = ownedRecord(userId, request.readingRecordId());
        if (reviewRepository.existsByUserIdAndReadingRecordId(userId, record.getId())) {
            throw ApiException.of(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        // 별점은 완독 여부와 무관하게 허용한다 (2026-09-01 정책 변경).
        // 신뢰 평점(검증 평점)은 VERIFIED_FULL 리뷰만 집계하므로 오염되지 않는다.
        Book book = bookRepository.findById(record.getBookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));

        var verification = verificationService.evaluate(record, book);

        Review review = reviewRepository.save(Review.builder()
                .userId(userId)
                .bookId(book.getId())
                .readingRecordId(record.getId())
                .rating(request.rating())
                .body(request.body())
                .tags(request.tags() == null ? new String[0] : request.tags().toArray(String[]::new))
                .hasSpoiler(Boolean.TRUE.equals(request.hasSpoiler()))
                .verificationLevel(verification.level())
                .verificationSnapshot(verification.toSnapshot())
                .build());

        return toView(review, userRepository.findById(userId).orElse(null));
    }

    @Transactional
    public ReviewView update(Long userId, Long reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        review.edit(request.body(), request.rating(),
                request.tags() == null ? null : request.tags().toArray(String[]::new),
                request.hasSpoiler());
        return toView(review, userRepository.findById(userId).orElse(null));
    }

    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        review.softDelete();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewView> listByBook(Long bookId, boolean verifiedOnly, Pageable pageable) {
        Page<Review> page = reviewRepository.findByBook(bookId, verifiedOnly, pageable);
        Map<Long, User> authors = loadAuthors(page.getContent());
        return PageResponse.of(page, review -> toView(review, authors.get(review.getUserId())));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewView> listMine(Long userId, Pageable pageable) {
        Page<Review> page = reviewRepository
                .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, "VISIBLE", pageable);
        User user = userRepository.findById(userId).orElse(null);
        return PageResponse.of(page, review -> toView(review, user));
    }

    @Transactional
    public void toggleHelpful(Long reviewId, boolean helpful) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (helpful) {
            review.addHelpful();
        } else {
            review.removeHelpful();
        }
    }

    @Transactional
    public void report(Long userId, Long reviewId, String reason, String detail) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (abuseReportRepository.existsByTargetTypeAndTargetIdAndReporterId(
                "REVIEW", reviewId, userId)) {
            throw ApiException.of(ErrorCode.CONFLICT);
        }
        abuseReportRepository.save(new AbuseReport("REVIEW", reviewId, userId, reason, detail));
        review.addReport();

        ModerationTicket ticket = moderationTicketRepository
                .findBySourceTypeAndSourceId(ModerationSource.REVIEW, reviewId)
                .orElseGet(() -> moderationTicketRepository.save(
                        new ModerationTicket(ModerationSource.REVIEW, reviewId, reason)));
        if (ticket.getReportCount() < review.getReportCount()) {
            ticket.addReport();
        }
        if (ticket.shouldAutoHide()) {
            review.hide();
        }
    }

    private ReadingRecord ownedRecord(Long userId, Long recordId) {
        ReadingRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RECORD_NOT_FOUND));
        if (!record.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        return record;
    }

    private Map<Long, User> loadAuthors(List<Review> reviews) {
        List<Long> ids = reviews.stream().map(Review::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    public ReviewView toView(Review review, User author) {
        return new ReviewView(
                review.getId(), review.getBookId(), review.getUserId(),
                author == null ? "알 수 없음" : author.getNickname(),
                author == null ? null : author.getHandle(),
                review.getRating(), review.getBody(),
                Arrays.asList(review.getTags()), review.isHasSpoiler(),
                review.getVerificationLevel(), review.getVerificationSnapshot(),
                review.getHelpfulCount(), review.getCreatedAt());
    }
}
