package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.NudgeRequest;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.club.*;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * 찌르기 (§12.4).
 *
 * <p>프리셋 문구만 허용하고, 같은 대상에게 24h 1회 · 하루 총 3회로 제한한다.
 * 공개 피드에는 남기지 않는다 — 수치심 유발을 막기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class ClubNudgeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ClubNudgeRepository nudgeRepository;
    private final ClubMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ClubService clubService;
    private final NotificationService notificationService;
    private final BookeyProperties properties;

    @Transactional
    public void nudge(Long fromUserId, Long clubId, NudgeRequest request) {
        Club club = clubService.getClub(clubId);
        if (!club.isAllowNudge()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "이 모임은 찌르기를 사용하지 않습니다.");
        }
        if (club.getStatus().isOver()) {
            throw ApiException.of(ErrorCode.CLUB_ENDED);
        }
        clubService.activeMember(clubId, fromUserId);

        if (fromUserId.equals(request.toUserId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "자신은 찌를 수 없습니다.");
        }
        ClubMember target = clubService.activeMember(clubId, request.toUserId());
        User targetUser = userRepository.findById(request.toUserId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        if (!target.isAllowNudge() || !targetUser.isAllowNudge()) {
            throw ApiException.of(ErrorCode.NUDGE_BLOCKED);
        }

        Instant cooldownFrom = Instant.now().minus(properties.club().nudgeCooldown());
        if (nudgeRepository.existsByFromUserIdAndToUserIdAndCreatedAtAfter(
                fromUserId, request.toUserId(), cooldownFrom)) {
            throw ApiException.of(ErrorCode.NUDGE_COOLDOWN);
        }
        Instant dayStart = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        if (nudgeRepository.countByFromUserIdAndCreatedAtAfter(fromUserId, dayStart)
                >= properties.club().nudgeDailyLimit()) {
            throw ApiException.of(ErrorCode.NUDGE_DAILY_LIMIT);
        }

        nudgeRepository.save(new ClubNudge(clubId, fromUserId, request.toUserId(),
                request.messageKey()));

        User sender = userRepository.findById(fromUserId).orElse(null);
        String senderName = sender == null ? "모임원" : sender.getNickname();

        notificationService.schedule(new NotificationService.NotificationRequest(
                request.toUserId(),
                NotificationType.CLUB_NUDGE,
                null, null, clubId,
                club.getName(),
                senderName + "님: " + request.messageKey().getText(),
                Map.of("clubId", clubId, "fromUserId", fromUserId,
                        "messageKey", request.messageKey().name()),
                null));
    }

    /** 남은 찌르기 횟수 — 클라이언트가 버튼 상태를 그릴 때 쓴다. */
    @Transactional(readOnly = true)
    public int remainingToday(Long fromUserId) {
        Instant dayStart = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        long used = nudgeRepository.countByFromUserIdAndCreatedAtAfter(fromUserId, dayStart);
        return (int) Math.max(0, properties.club().nudgeDailyLimit() - used);
    }
}
