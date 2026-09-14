package app.bookey.batch;

import app.bookey.api.club.ClubCheckpointService;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** 모임 배치 — 체크포인트 마감 평가 · 임박 알림 · 기간 종료 처리 (§F12). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubScheduleJob {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ClubCheckpointService checkpointService;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubEventRepository eventRepository;
    private final NotificationService notificationService;
    private final ClubPostRepository postRepository;

    /** 매시 정각 — 마감된 체크포인트를 평가한다. */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void evaluateCheckpoints() {
        int count = checkpointService.evaluateDue(Instant.now());
        if (count > 0) {
            log.info("ClubScheduleJob: {} checkpoints evaluated", count);
        }
    }

    /** 매일 저녁 — 24시간 뒤 마감인 체크포인트 미달자에게 임박 알림. */
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void notifyUpcomingCheckpoints() {
        Instant from = Instant.now();
        Instant to = from.plus(24, ChronoUnit.HOURS);
        int notified = checkpointService.notifyUpcoming(from, to);
        if (notified > 0) {
            log.info("ClubScheduleJob: {} checkpoint reminders scheduled", notified);
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
                        club.getName() + " — 이번 주 읽기로그",
                        "함께 읽은 일주일이 카드 한 장으로 모였어요",
                        Map.of("clubId", club.getId(), "weekOf", monday.toString()), null));
                notified++;
            }
        }
        if (notified > 0) {
            log.info("ClubScheduleJob: {} weekly log cards scheduled", notified);
        }
    }

    /** 매일 새벽 — 기간이 끝난 모임을 종료하고 결산 알림을 보낸다. */
    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void closeExpiredClubs() {
        List<Club> expired = clubRepository.findExpired(LocalDate.now(KST));
        for (Club club : expired) {
            club.end();
            eventRepository.save(new ClubEvent(club.getId(), null, ClubEventType.ENDED, Map.of()));
            for (ClubMember member :
                    memberRepository.findAllByClubIdAndStatus(club.getId(), ClubMemberStatus.ACTIVE)) {
                notificationService.schedule(new NotificationService.NotificationRequest(
                        member.getUserId(), NotificationType.CLUB_ENDED, null, null, club.getId(),
                        club.getName() + " 모임이 끝났어요",
                        "결산 카드를 확인하고 다음 책으로 이어가 보세요",
                        Map.of("clubId", club.getId()), null));
            }
        }
        if (!expired.isEmpty()) {
            log.info("ClubScheduleJob: {} clubs closed", expired.size());
        }
    }
}
