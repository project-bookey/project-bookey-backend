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
import app.bookey.domain.review.ReviewCommentRepository;
import app.bookey.domain.review.ReviewCommentRepository.CommentCount;
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
    private final ReviewCommentRepository reviewCommentRepository;
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

        return toView(review, userRepository.findById(userId).orElse(null), 0L);
    }

    @Transactional
    public ReviewView update(Long userId, Long reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        review.edit(request.body(), request.rating(),
                request.tags() == null ? null : request.tags().toArray(String[]::new),
                request.hasSpoiler());
        return toView(review, userRepository.findById(userId).orElse(null),
                loadCommentCounts(List.of(review)).getOrDefault(review.getId(), 0L));
    }

    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.REVIEW_NOT_FOUND));
        if (!review.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        review.softDelete();
    }

    /** 리뷰 단건 — 숨겨지거나 지워진 리뷰는 없는 것으로 본다. */
    @Transactional(readOnly = true)
    public ReviewView detail(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(Review::isVisible)
                .orElseThrow(() -> ApiException.of(ErrorCode.REVIEW_NOT_FOUND));
        return toView(review, userRepository.findById(review.getUserId()).orElse(null),
                loadCommentCounts(List.of(review)).getOrDefault(reviewId, 0L));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewView> listByBook(Long bookId, boolean verifiedOnly, Pageable pageable) {
        Page<Review> page = reviewRepository.findByBook(bookId, verifiedOnly, pageable);
        Map<Long, User> authors = loadAuthors(page.getContent());
        Map<Long, Long> commentCounts = loadCommentCounts(page.getContent());
        return PageResponse.of(page, review -> toView(review, authors.get(review.getUserId()),
                commentCounts.getOrDefault(review.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewView> listMine(Long userId, Pageable pageable) {
        Page<Review> page = reviewRepository
                .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, "VISIBLE", pageable);
        User user = userRepository.findById(userId).orElse(null);
        Map<Long, Long> commentCounts = loadCommentCounts(page.getContent());
        return PageResponse.of(page, review -> toView(review, user,
                commentCounts.getOrDefault(review.getId(), 0L)));
    }

    @Transactional
    public void toggleHelpful(Long reviewId, boolean helpful) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.REVIEW_NOT_FOUND));
        if (helpful) {
            review.addHelpful();
        } else {
            review.removeHelpful();
        }
    }

    @Transactional
    public void report(Long userId, Long reviewId, String reason, String detail) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.REVIEW_NOT_FOUND));
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

    /** 리뷰별 댓글 수 — 답글까지 포함한 전체 수를 한 번에 모은다(QuoteService.loadCommentCounts 선례). */
    private Map<Long, Long> loadCommentCounts(List<Review> reviews) {
        List<Long> ids = reviews.stream().map(Review::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return reviewCommentRepository.countPerReview(ids).stream()
                .collect(Collectors.toMap(CommentCount::getReviewId, CommentCount::getCommentCount));
    }

    /** 탈퇴한 작성자는 "알 수 없음", 댓글 수는 배치 맵에서 결측 시 0으로 채워 넣는다. */
    public static ReviewView toView(Review review, User author, long commentCount) {
        return new ReviewView(
                review.getId(), review.getBookId(), review.getUserId(),
                author == null ? "알 수 없음" : author.getNickname(),
                author == null ? null : author.getHandle(),
                review.getRating(), review.getBody(),
                Arrays.asList(review.getTags()), review.isHasSpoiler(),
                review.getVerificationLevel(), review.getVerificationSnapshot(),
                review.getHelpfulCount(), commentCount, review.getCreatedAt());
    }
}
