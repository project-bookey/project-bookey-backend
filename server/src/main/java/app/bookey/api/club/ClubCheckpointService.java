package app.bookey.api.club;

import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.reading.ReadingRecord;
import app.bookey.domain.reading.ReadingRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 체크포인트 마감 처리 (§12.2).
 * 마감 시각에 각 멤버의 진도를 스냅샷으로 굳힌다 — 사후에 진도를 올려도 결과는 바뀌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubCheckpointService {

    private final ClubCheckpointRepository checkpointRepository;
    private final ClubCheckpointProgressRepository progressRepository;
    private final ClubBookRepository clubBookRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubPostRepository postRepository;
    private final ClubEventRepository eventRepository;
    private final ClubRepository clubRepository;
    private final ReadingRecordRepository recordRepository;
    private final BookRepository bookRepository;
    private final NotificationService notificationService;

    /** 마감된 체크포인트를 평가한다. 배치에서 호출. */
    @Transactional
    public int evaluateDue(Instant now) {
        List<ClubCheckpoint> due = checkpointRepository.findDue(now);
        for (ClubCheckpoint checkpoint : due) {
            try {
                evaluate(checkpoint);
            } catch (Exception e) {
                log.error("Checkpoint evaluation failed: id={}", checkpoint.getId(), e);
            }
        }
        return due.size();
    }

    @Transactional
    public void evaluate(ClubCheckpoint checkpoint) {
        ClubBook clubBook = clubBookRepository.findById(checkpoint.getClubBookId())
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        Club club = clubRepository.findById(clubBook.getClubId())
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));

        List<ClubMember> members =
                memberRepository.findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE);
        Set<Long> alreadyEvaluated = progressRepository.findAllByCheckpointId(checkpoint.getId())
                .stream()
                .map(ClubCheckpointProgress::getClubMemberId)
                .collect(java.util.stream.Collectors.toSet());

        long achievedCount = 0;
        for (ClubMember member : members) {
            if (alreadyEvaluated.contains(member.getId())) {
                continue;
            }
            int page = currentPage(member);
            boolean achieved = page >= checkpoint.getTargetPage();
            if (achieved) {
                achievedCount++;
            }
            progressRepository.save(
                    new ClubCheckpointProgress(checkpoint.getId(), member.getId(), page, achieved));

            eventRepository.save(new ClubEvent(club.getId(), member.getUserId(),
                    achieved ? ClubEventType.CHECKPOINT_MET : ClubEventType.CHECKPOINT_MISSED,
                    Map.of("checkpointId", checkpoint.getId(), "page", page)));

            notifyResult(club, checkpoint, member, achieved, page);
        }
        checkpoint.markEvaluated();

        // 회고 스레드 자동 생성 (§12.3 CHECKPOINT 타입)
        postRepository.save(ClubPost.builder()
                .clubId(club.getId())
                .clubBookId(clubBook.getId())
                .userId(club.getOwnerId())
                .type(ClubPostType.CHECKPOINT)
                .body(checkpoint.getTitle() + " 마감! " + checkpoint.getTargetPage() + "쪽까지 어떠셨나요?")
                .anchorPage(checkpoint.getTargetPage())
                .spoilerLevel(SpoilerLevel.PAGE)
                .build());

        log.info("Checkpoint {} evaluated: {}/{} achieved",
                checkpoint.getId(), achievedCount, members.size());
    }

    /**
     * 결과 알림. 미달자에게는 <b>개인 알림으로만</b> 전달한다 —
     * 공개적으로 낙오자를 지목하지 않는다(§12.4 설계 원칙).
     */
    private void notifyResult(Club club, ClubCheckpoint checkpoint, ClubMember member,
                              boolean achieved, int page) {
        String body = achieved
                ? checkpoint.getTitle() + " 목표 달성! 👏"
                : checkpoint.getTitle() + "은 " + checkpoint.getTargetPage() + "쪽까지였어요. "
                        + "지금 " + page + "쪽 — 지금 시작하면 따라잡을 수 있어요";

        notificationService.schedule(new NotificationService.NotificationRequest(
                member.getUserId(),
                achieved ? NotificationType.CLUB_CHECKPOINT_RESULT : NotificationType.CLUB_FALLBEHIND,
                null, member.getReadingRecordId(), club.getId(),
                club.getName(), body,
                Map.of("clubId", club.getId(), "checkpointId", checkpoint.getId(),
                        "achieved", achieved),
                null));
    }

    /** 마감 24h 전 미달자에게 임박 알림. */
    @Transactional
    public int notifyUpcoming(Instant from, Instant to) {
        List<ClubCheckpoint> upcoming = checkpointRepository.findUpcoming(from, to);
        int notified = 0;
        for (ClubCheckpoint checkpoint : upcoming) {
            ClubBook clubBook = clubBookRepository.findById(checkpoint.getClubBookId()).orElse(null);
            if (clubBook == null) {
                continue;
            }
            Club club = clubRepository.findById(clubBook.getClubId()).orElse(null);
            if (club == null || club.getStatus().isOver()) {
                continue;
            }
            for (ClubMember member : memberRepository
                    .findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE)) {
                int page = currentPage(member);
                if (page >= checkpoint.getTargetPage()) {
                    continue;
                }
                notificationService.schedule(new NotificationService.NotificationRequest(
                        member.getUserId(), NotificationType.CLUB_CHECKPOINT_DUE, null,
                        member.getReadingRecordId(), club.getId(),
                        club.getName(),
                        checkpoint.getTitle() + " 마감이 하루 남았어요 — "
                                + (checkpoint.getTargetPage() - page) + "쪽 남음",
                        Map.of("clubId", club.getId(), "checkpointId", checkpoint.getId()), null));
                notified++;
            }
        }
        return notified;
    }

    private int currentPage(ClubMember member) {
        if (member.getReadingRecordId() == null) {
            return 0;
        }
        return recordRepository.findById(member.getReadingRecordId())
                .map(ReadingRecord::getCurrentPage)
                .orElse(0);
    }

    /** 호스트가 체크포인트를 다시 구성한다. 이미 평가된 항목은 건드리지 않는다. */
    @Transactional
    public void replaceCheckpoints(Long clubId, List<ClubCheckpoint> newCheckpoints) {
        ClubBook clubBook = clubBookRepository.findFirstByClubIdOrderBySeqAsc(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        List<ClubCheckpoint> existing =
                checkpointRepository.findAllByClubBookIdOrderBySeqAsc(clubBook.getId());
        existing.stream()
                .filter(c -> c.getEvaluatedAt() == null)
                .forEach(checkpointRepository::delete);
        newCheckpoints.forEach(checkpointRepository::save);
    }

    /** 모임 도서의 총 페이지를 최신 값으로 맞춘다(크라우드 입력으로 나중에 채워지는 경우). */
    @Transactional
    public void syncTotalPages(Long clubId) {
        clubBookRepository.findFirstByClubIdOrderBySeqAsc(clubId).ifPresent(clubBook -> {
            Book book = bookRepository.findById(clubBook.getBookId()).orElse(null);
            if (book != null && book.hasTotalPages()) {
                clubBook.updateTotalPages(book.getTotalPages());
            }
        });
    }
}
