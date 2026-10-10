package app.bookey.api.push;

import app.bookey.admin.support.AdminAuditService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.notification.NotificationService.NotificationRequest;
import app.bookey.api.push.dto.PushCampaignDtos.*;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.push.PushCampaign;
import app.bookey.domain.push.PushCampaignKind;
import app.bookey.domain.push.PushCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 관리자 전체 푸시(캠페인) — 만들기·고치기·취소·테스트 발송. 실제 발송은 {@link PushCampaignRunner} 가 잡에서 한다.
 * 되돌릴 수 없는 대량 발송이라 최고 관리자만 한다.
 */
@Service
@RequiredArgsConstructor
public class PushCampaignService {

    /** 예약 시각이 이만큼 지났으면 실수로 본다(바로 보내려면 비워 둔다). */
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);

    private final PushCampaignRepository campaignRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final AdminRepository adminRepository;
    private final AdminAuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<PushCampaignRow> list(AuthAdmin admin, int page, int size) {
        requireBroadcast(admin);
        var result = campaignRepository.findAllByOrderByIdDesc(PageRequest.of(page, Math.clamp(size, 1, 100)));
        Map<Long, String> names = adminNames(result.getContent().stream().map(PushCampaign::getCreatedBy).toList());
        return PageResponse.of(result, c -> toRow(c, names));
    }

    @Transactional(readOnly = true)
    public PushCampaignView detail(AuthAdmin admin, Long id) {
        requireBroadcast(admin);
        PushCampaign campaign = campaignRepository.findById(id).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return new PushCampaignView(toRow(campaign, adminNames(List.of(campaign.getCreatedBy()))),
                PushMessages.title(campaign.getKind(), campaign.getTitle()),
                PushMessages.body(campaign.getKind(), campaign.getBody()),
                notificationRepository.countByCampaignIdAndSentAtIsNotNull(id),
                notificationRepository.countByCampaignIdAndSentAtIsNull(id),
                notificationRepository.countByCampaignIdAndOpenedAtIsNotNull(id));
    }

    @Transactional(readOnly = true)
    public PushAudienceView audience(AuthAdmin admin, PushCampaignKind kind) {
        requireBroadcast(admin);
        boolean marketing = kind == PushCampaignKind.MARKETING;
        return new PushAudienceView(campaignRepository.countAudience(marketing),
                campaignRepository.countAudienceWithDevice(marketing));
    }

    @Transactional
    public PushCampaignRow create(AuthAdmin admin, PushCampaignRequest req) {
        requireBroadcast(admin);
        Instant now = clock.instant();
        Instant scheduledAt = scheduleOf(req.scheduledAt(), now);
        PushCampaign campaign = campaignRepository.save(new PushCampaign(req.kind(), req.title().trim(),
                req.body().trim(), link(req.linkUrl()), scheduledAt, admin.id()));
        auditService.log(admin, "CREATE_PUSH_CAMPAIGN", "PUSH_CAMPAIGN", campaign.getId(), req.reason(), null,
                snapshot(campaign));
        return toRow(campaign, adminNames(List.of(admin.id())));
    }

    @Transactional
    public PushCampaignRow update(AuthAdmin admin, Long id, PushCampaignRequest req) {
        requireBroadcast(admin);
        PushCampaign campaign = campaignRepository.findById(id).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!campaign.isEditable()) {
            throw new ApiException(ErrorCode.CONFLICT, "보내기 시작한 캠페인은 고칠 수 없습니다. 취소하고 새로 만드세요.");
        }
        Map<String, Object> before = snapshot(campaign);
        campaign.edit(req.kind(), req.title().trim(), req.body().trim(), link(req.linkUrl()),
                scheduleOf(req.scheduledAt(), clock.instant()));
        auditService.log(admin, "UPDATE_PUSH_CAMPAIGN", "PUSH_CAMPAIGN", id, req.reason(), before, snapshot(campaign));
        return toRow(campaign, adminNames(List.of(campaign.getCreatedBy())));
    }

    /** 취소 — 아직 안 나간 알림(미뤄 둔 것 포함)은 지운다. 이미 나간 푸시는 되돌릴 수 없다. */
    @Transactional
    public PushCampaignRow cancel(AuthAdmin admin, Long id, String reason) {
        requireBroadcast(admin);
        if (reason == null || reason.isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        PushCampaign campaign = campaignRepository.findById(id).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!campaign.isCancellable()) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 끝났거나 취소한 캠페인입니다.");
        }
        Map<String, Object> before = snapshot(campaign);
        campaign.cancel(admin.id(), clock.instant());
        int removed = notificationRepository.deleteUnsentByCampaign(id);
        auditService.log(admin, "CANCEL_PUSH_CAMPAIGN", "PUSH_CAMPAIGN", id, reason, before,
                Map.of("status", campaign.getStatus().name(), "removedUnsent", removed));
        return toRow(campaign, adminNames(List.of(campaign.getCreatedBy())));
    }

    /** 테스트 발송 — 지정한 회원에게만 '[테스트]' 를 붙여 바로 보낸다(일반 알림 경로, 푸시 킬스위치를 따른다). */
    @Transactional
    public int test(AuthAdmin admin, PushTestRequest req) {
        requireBroadcast(admin);
        String title = "[테스트] " + PushMessages.title(req.kind(), req.title());
        String body = PushMessages.body(req.kind(), req.body());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("test", true);
        String link = link(req.linkUrl());
        if (link != null) {
            payload.put("link", link);
        }
        int delivered = 0;
        for (Long userId : req.userIds().stream().distinct().toList()) {
            if (notificationService.inApp(new NotificationRequest(userId, NotificationType.ANNOUNCEMENT, null, null,
                    null, title, body, payload, null)).isPresent()) {
                delivered++;
            }
        }
        auditService.log(admin, "TEST_PUSH_CAMPAIGN", "PUSH_CAMPAIGN", null, null, null,
                Map.of("userIds", req.userIds(), "delivered", delivered, "title", title));
        return delivered;
    }

    // ── 내부 ────────────────────────────────────────────────

    private static Instant scheduleOf(Instant requested, Instant now) {
        if (requested == null) {
            return now;
        }
        if (requested.isBefore(now.minus(PAST_TOLERANCE))) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지난 시각으로는 예약할 수 없습니다. 바로 보내려면 비워 두세요.");
        }
        return requested;
    }

    /** 링크는 https 주소나 앱 화면 경로(/…)만 — 앱이 아무 스킴이나 열지 않게. */
    static String link(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("https://") && !(trimmed.startsWith("/") && !trimmed.startsWith("//"))) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "링크는 https:// 주소나 / 로 시작하는 앱 화면 경로만 쓸 수 있습니다.");
        }
        return trimmed;
    }

    private PushCampaignRow toRow(PushCampaign c, Map<Long, String> names) {
        return new PushCampaignRow(c.getId(), c.getKind(), c.getTitle(), c.getBody(), c.getLinkUrl(), c.getStatus(),
                c.getScheduledAt(), c.getStartedAt(), c.getFinishedAt(), c.getTargetCount(), c.getCreatedBy(),
                names.get(c.getCreatedBy()), c.getCreatedAt());
    }

    private Map<Long, String> adminNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return adminRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Admin::getId, Admin::getName, (a, b) -> a));
    }

    private static Map<String, Object> snapshot(PushCampaign c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", c.getKind().name());
        map.put("title", c.getTitle());
        map.put("body", c.getBody());
        map.put("linkUrl", c.getLinkUrl());
        map.put("scheduledAt", c.getScheduledAt().toString());
        map.put("status", c.getStatus().name());
        return map;
    }

    private static void requireBroadcast(AuthAdmin admin) {
        if (!admin.role().canBroadcast()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
