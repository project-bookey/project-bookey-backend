package app.bookey.admin;

import app.bookey.admin.dto.AdminContentDtos.AbuseReportRow;
import app.bookey.admin.dto.AdminContentDtos.ModerationDetailView;
import app.bookey.admin.dto.AdminDtos.*;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.*;
import app.bookey.domain.report.AbuseReport;
import app.bookey.domain.report.AbuseReportRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 신고 큐 처리 (§F13 신고 큐 · §8.3). SLA 48h. */
@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final ModerationTicketRepository ticketRepository;
    private final AbuseReportRepository abuseReportRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final UserSanctionRepository sanctionRepository;
    private final AdminUserService adminUserService;
    private final AdminContentService contentService;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ModerationRow> queue(ModerationStatus status, ModerationSource sourceType,
                                             int page, int size) {
        // 우선순위가 높고 마감이 가까운 건을 위로 올린다.
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("priority"), Sort.Order.asc("slaDueAt")));

        Page<ModerationTicket> tickets;
        if (status != null && sourceType != null) {
            tickets = ticketRepository.findAllByStatusAndSourceType(status, sourceType, pageable);
        } else if (status != null) {
            tickets = ticketRepository.findAllByStatus(status, pageable);
        } else if (sourceType != null) {
            tickets = ticketRepository.findAllBySourceType(sourceType, pageable);
        } else {
            tickets = ticketRepository.findAllBy(pageable);
        }
        Map<Long, String> names = adminNames(tickets.getContent());
        return PageResponse.of(tickets, ticket -> toRow(ticket, names));
    }

    /**
     * 신고 상세 — 신고 하나하나(신고자·사유·상세)와 원문, 작성자의 제재 이력. 열람 기록(VIEW_MODERATION)이 남는다.
     * CS 담당(경고 권한)부터 볼 수 있고, 판정은 신고 처리 권한이 있어야 한다.
     */
    @Transactional
    public ModerationDetailView detail(AuthAdmin admin, Long ticketId) {
        if (!admin.role().canWarn()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        ModerationTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        ModerationRow row = toRow(ticket, adminNames(List.of(ticket)));

        List<AbuseReport> reports = abuseReportRepository.findAllByTargetTypeAndTargetIdOrderByIdDesc(
                ticket.getSourceType().name(), ticket.getSourceId());
        Map<Long, String> reporters = userRepository.findAllById(
                        reports.stream().map(AbuseReport::getReporterId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

        List<SanctionRow> sanctions = row.authorId() == null ? List.of()
                : sanctionRepository.findAllByUserIdOrderByCreatedAtDesc(row.authorId()).stream()
                        .map(s -> new SanctionRow(s.getId(), s.getType(), s.getReason(), s.getStartsAt(),
                                s.getEndsAt(), s.getReleasedAt(), s.getAdminId()))
                        .toList();

        auditService.log(admin, "VIEW_MODERATION", ticket.getSourceType().name(), ticket.getSourceId(),
                null, null, Map.of("ticketId", ticketId));
        return new ModerationDetailView(row,
                contentService.detailForTicket(admin, ticket.getSourceType(), ticket.getSourceId()),
                reports.stream().map(r -> toReportRow(r, reporters)).toList(),
                sanctions);
    }

    /** 한 사람이 신고한 내역 — 신고를 남발하는지 볼 때. */
    @Transactional(readOnly = true)
    public PageResponse<AbuseReportRow> reportsBy(AuthAdmin admin, Long reporterId, int page, int size) {
        if (!admin.role().canWarn()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        var result = abuseReportRepository.findAllByReporterIdOrderByIdDesc(reporterId,
                PageRequest.of(page, Math.clamp(size, 1, 100)));
        Map<Long, String> reporters = userRepository.findById(reporterId)
                .map(u -> Map.of(u.getId(), u.getNickname())).orElse(Map.of());
        return PageResponse.of(result, r -> toReportRow(r, reporters));
    }

    private static AbuseReportRow toReportRow(AbuseReport r, Map<Long, String> reporters) {
        return new AbuseReportRow(r.getId(), r.getTargetType(), r.getTargetId(), r.getReporterId(),
                reporters.get(r.getReporterId()), r.getReason(), r.getDetail(), r.getStatus(), r.getCreatedAt());
    }

    private Map<Long, String> adminNames(List<ModerationTicket> tickets) {
        List<Long> ids = tickets.stream().map(ModerationTicket::getAssignedAdminId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return adminRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Admin::getId, Admin::getName, (a, b) -> a));
    }

    private ModerationRow toRow(ModerationTicket ticket, Map<Long, String> adminNames) {
        var summary = contentService.summarize(ticket.getSourceType(), ticket.getSourceId());
        String preview = summary.map(AdminContentService.Summary::preview).orElse("(삭제됨)");
        Long authorId = summary.map(AdminContentService.Summary::authorId).orElse(null);
        String authorNickname = authorId == null ? null : userRepository.findById(authorId)
                .map(User::getNickname).orElse(null);

        return new ModerationRow(ticket.getId(), ticket.getSourceType(), ticket.getSourceId(),
                ticket.getReason(), ticket.getReportCount(), ticket.getPriority(),
                ticket.getSlaDueAt(), ticket.isOverdue(), ticket.getStatus(),
                ticket.getAssignedAdminId(), preview, authorId, authorNickname,
                ticket.getCreatedAt(), ticket.getResolution(), ticket.getResolutionNote(), ticket.getResolvedAt(),
                ticket.getAssignedAdminId() == null ? null : adminNames.get(ticket.getAssignedAdminId()));
    }

    @Transactional
    public void assign(AuthAdmin admin, Long ticketId) {
        requireModerator(admin);
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND))
                .assign(admin.id());
    }

    @Transactional
    public void resolve(AuthAdmin admin, Long ticketId, ResolveRequest request) {
        requireModerator(admin);
        ModerationTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("ticketId", ticket.getId());
        before.put("status", ticket.getStatus().name());
        before.put("reportCount", ticket.getReportCount());
        before.put("assignedAdminId", ticket.getAssignedAdminId());

        Long authorId = applyResolution(ticket, request.resolution());

        if (request.resolution() == ModerationResolution.SANCTION) {
            if (request.sanction() == null || authorId == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "제재 대상과 사유가 필요합니다.");
            }
            adminUserService.sanction(admin, authorId, request.sanction());
        }

        ticket.resolve(admin.id(), request.resolution(), request.note());
        abuseReportRepository.resolveAllForTarget(
                ticket.getSourceType().name(), ticket.getSourceId());

        auditService.log(admin, "RESOLVE_MODERATION", ticket.getSourceType().name(),
                ticket.getSourceId(), request.note(), before,
                Map.of("resolution", request.resolution().name(), "status", ticket.getStatus().name()));
    }

    /** @return 제재를 걸 대상(콘텐츠 작성자·모임 호스트·신고된 회원). 없으면 null. */
    private Long applyResolution(ModerationTicket ticket, ModerationResolution resolution) {
        return contentService.applyResolution(ticket.getSourceType(), ticket.getSourceId(), resolution);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return ticketRepository.countByStatus(ModerationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long overdueCount() {
        return ticketRepository.countOverdue(Instant.now());
    }

    private void requireModerator(AuthAdmin admin) {
        if (!admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
