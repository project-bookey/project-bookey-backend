package app.bookey.batch;

import app.bookey.api.club.ClubService;
import app.bookey.api.notification.NotificationService;
import app.bookey.domain.club.*;
import app.bookey.domain.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * 모임 배치 — 지금 읽는 책 맞추기 · 주간 카드 알림 (§F12).
 * 모임은 기간 없이 이어지므로 기간 종료 처리와 체크포인트 평가는 걷어냈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubScheduleJob {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ClubService clubService;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final NotificationService notificationService;
    private final ClubPostRepository postRepository;

    /**
     * 매일 자정 직후 — 만남 날이 지나면 다음 만남의 책으로 넘어가도록 모임마다 지금 읽는 책을 맞춘다.
     * 만남을 열고 · 고치고 · 취소할 때도 바로 맞추므로, 여기서는 날짜가 넘어가는 경우만 잡으면 된다.
     * 한 모임이 실패해도 나머지는 계속한다.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void syncCurrentBooks() {
        int failed = 0;
        for (Long clubId : clubRepository.findOngoingIds()) {
            try {
                clubService.syncCurrentBook(clubId);
            } catch (RuntimeException e) {
                failed++;
                log.warn("ClubScheduleJob: 지금 읽는 책을 맞추지 못했습니다 clubId={}", clubId, e);
            }
        }
        if (failed > 0) {
            log.warn("ClubScheduleJob: {} clubs failed to sync current book", failed);
        }
    }

    /**
     * 일요일 밤 — 이번 주 조각이 있는 진행 중 모임의 멤버에게 주간 카드 알림.
     * 모임 알림 한도(모임당 하루 1건)는 NotificationService 가 건다.
     */
    @Scheduled(cron = "0 0 21 * * SUN", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void notifyWeeklyLogCards() {
        LocalDate today = LocalDate.now(KST);
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        Instant from = monday.atStartOfDay(KST).toInstant();
        Instant to = monday.plusDays(7).atStartOfDay(KST).toInstant();

        int notified = 0;
        for (Club club : clubRepository.findAllById(postRepository.findClubIdsWithLogsBetween(from, to))) {
            if (club.getStatus().isOver()) {
                continue;
            }
            for (ClubMember member :
                    memberRepository.findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE)) {
                notificationService.schedule(new NotificationService.NotificationRequest(
                        member.getUserId(), NotificationType.CLUB_WEEKLY_LOG, null, null, club.getId(),
                        club.getName() + " · 이번 주 카드",
                        "함께 읽은 일주일이 카드 한 장으로 모였어요",
                        Map.of("clubId", club.getId(), "weekOf", monday.toString()), null));
                notified++;
            }
        }
        if (notified > 0) {
            log.info("ClubScheduleJob: {} weekly log cards scheduled", notified);
        }
    }
}
