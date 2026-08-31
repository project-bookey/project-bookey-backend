package app.bookey.api.session;

import app.bookey.api.session.dto.SessionDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.api.library.ProgressService;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.ClubMember;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubMemberStatus;
import app.bookey.domain.notification.Notification;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.reading.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 독서 세션 기록 (§F3). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ReadingSessionRepository sessionRepository;
    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final NotificationRepository notificationRepository;
    private final ProgressService progressService;

    @Transactional
    public SessionView start(Long userId, StartRequest request) {
        // 오프라인 동기화 멱등 처리 — 같은 client_uuid 면 기존 세션을 돌려준다.
        if (request.clientUuid() != null) {
            var existing = sessionRepository.findByUserIdAndClientUuid(userId, request.clientUuid());
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }
        if (sessionRepository.findByUserIdAndEndedAtIsNull(userId).isPresent()) {
            throw ApiException.of(ErrorCode.SESSION_ALREADY_OPEN);
        }
        ReadingRecord record = ownedRecord(userId, request.readingRecordId());

        int startPage = request.startPage() == null ? record.getCurrentPage() : request.startPage();
        ReadingSession session = ReadingSession.builder()
                .readingRecordId(record.getId())
                .userId(userId)
                .startedAt(request.startedAt())
                .startPage(startPage)
                .source(SessionSource.TIMER)
                .clientUuid(request.clientUuid())
                .build();
        session = sessionRepository.save(session);

        record.startReading(session.getStartedAt());
        return toView(session);
    }

    @Transactional(readOnly = true)
    public SessionView current(Long userId) {
        return sessionRepository.findByUserIdAndEndedAtIsNull(userId)
                .map(this::toView)
                .orElse(null);
    }

    @Transactional
    public SessionEndResult end(Long userId, Long sessionId, EndRequest request) {
        ReadingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        ReadingRecord record = ownedRecord(userId, session.getReadingRecordId());
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        int totalPages = record.effectiveTotalPages(book == null ? null : book.getTotalPages());

        validatePage(request.endPage(), session.getStartPage(), totalPages);

        session.close(request.endedAt(), request.endPage(), request.foregroundRatio(),
                request.interactionCount(), request.memo());

        return finalizeSession(userId, session, record, totalPages);
    }

    /** 수동 기록 (§F3). 검증 등급 산정에서 가중치가 낮다. */
    @Transactional
    public SessionEndResult recordManual(Long userId, ManualRequest request) {
        if (request.clientUuid() != null) {
            var existing = sessionRepository.findByUserIdAndClientUuid(userId, request.clientUuid());
            if (existing.isPresent()) {
                ReadingRecord record = ownedRecord(userId, existing.get().getReadingRecordId());
                Book book = bookRepository.findById(record.getBookId()).orElse(null);
                return finalizeSession(userId, existing.get(), record,
                        record.effectiveTotalPages(book == null ? null : book.getTotalPages()));
            }
        }
        ReadingRecord record = ownedRecord(userId, request.readingRecordId());
        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        int totalPages = record.effectiveTotalPages(book == null ? null : book.getTotalPages());

        int startPage = request.startPage() == null ? record.getCurrentPage() : request.startPage();
        validatePage(request.endPage(), startPage, totalPages);

        ReadingSession session = ReadingSession.builder()
                .readingRecordId(record.getId())
                .userId(userId)
                .startedAt(request.startedAt())
                .startPage(startPage)
                .source(SessionSource.MANUAL)
                .clientUuid(request.clientUuid())
                .build();
        session.closeManual(request.startedAt().plusSeconds(request.durationSec()),
                request.durationSec(), request.endPage(), request.memo());
        session = sessionRepository.save(session);

        return finalizeSession(userId, session, record, totalPages);
    }

    private SessionEndResult finalizeSession(Long userId, ReadingSession session,
                                             ReadingRecord record, int totalPages) {
        Instant readAt = session.getEndedAt() == null ? Instant.now() : session.getEndedAt();
        if (session.getEndPage() != null) {
            record.applyProgress(session.getEndPage(), readAt);
        } else {
            record.applyProgress(record.getCurrentPage(), readAt);
        }

        boolean finished = totalPages > 0 && record.getCurrentPage() >= totalPages;
        if (finished && record.getStatus() != ReadingStatus.FINISHED) {
            record.finish(readAt, totalPages);
        }

        List<ClubProgressEcho> clubs = syncClubs(record, readAt);
        markNotificationConversions(userId);

        Book book = bookRepository.findById(record.getBookId()).orElse(null);
        var progress = progressService.calculate(record, book);

        return new SessionEndResult(
                toView(session),
                record.getCurrentPage(),
                progress.completionRate(),
                progress.lagLevel().name(),
                finished,
                clubs);
    }

    /** 모임 멤버십에 마지막 독서 시각을 반영하고, 내 순위를 계산해 돌려준다(§12.2). */
    private List<ClubProgressEcho> syncClubs(ReadingRecord record, Instant readAt) {
        List<ClubMember> memberships = clubMemberRepository
                .findAllByReadingRecordIdAndStatus(record.getId(), ClubMemberStatus.ACTIVE);
        List<ClubProgressEcho> result = new ArrayList<>();
        for (ClubMember membership : memberships) {
            membership.touchLastRead(readAt);
            List<ClubMember> peers = clubMemberRepository
                    .findAllByClubIdAndStatus(membership.getClubId(), ClubMemberStatus.ACTIVE);
            int rank = calculateRank(record, peers);
            result.add(new ClubProgressEcho(membership.getClubId(), null, rank, peers.size()));
        }
        return result;
    }

    private int calculateRank(ReadingRecord myRecord, List<ClubMember> peers) {
        List<Long> recordIds = peers.stream()
                .map(ClubMember::getReadingRecordId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (recordIds.isEmpty()) {
            return 1;
        }
        List<ReadingRecord> records = recordRepository.findAllByIdIn(recordIds);
        long ahead = records.stream()
                .filter(r -> r.getCurrentPage() > myRecord.getCurrentPage())
                .count();
        return (int) ahead + 1;
    }

    /** 알림 발송 후 24h 내 세션 발생 → 전환 마킹 (§F5 실험 프레임). */
    private void markNotificationConversions(Long userId) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Notification> convertible = notificationRepository.findConvertible(userId, since);
        convertible.forEach(Notification::markConverted);
    }

    @Transactional(readOnly = true)
    public List<SessionView> listByRecord(Long userId, Long recordId) {
        ownedRecord(userId, recordId);
        return sessionRepository.findAllByReadingRecordIdOrderByStartedAtDesc(recordId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SessionView> listRecent(Long userId, Pageable pageable) {
        return sessionRepository.findAllByUserIdOrderByStartedAtDesc(userId, pageable)
                .map(this::toView)
                .getContent();
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        ReadingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.of(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw ApiException.of(ErrorCode.FORBIDDEN);
        }
        sessionRepository.delete(session);
    }

    private void validatePage(Integer endPage, Integer startPage, int totalPages) {
        if (endPage == null) {
            return;
        }
        if (endPage < 0 || (totalPages > 0 && endPage > totalPages)) {
            throw ApiException.of(ErrorCode.INVALID_PAGE_RANGE);
        }
        if (startPage != null && endPage < startPage) {
            // 뒤로 읽기는 허용하되 진도는 낮추지 않는다(§8.1). 세션 자체는 기록된다.
            log.debug("Backward reading recorded: {} -> {}", startPage, endPage);
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

    public SessionView toView(ReadingSession session) {
        return new SessionView(
                session.getId(),
                session.getReadingRecordId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSec(),
                session.getStartPage(),
                session.getEndPage(),
                session.readPages(),
                session.getSource(),
                session.getMemo(),
                session.getAbuseFlags(),
                session.isCountedForVerification());
    }
}
