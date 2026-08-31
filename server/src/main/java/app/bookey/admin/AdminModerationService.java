package app.bookey.admin;

import app.bookey.admin.dto.AdminDtos.*;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.*;
import app.bookey.domain.club.ClubPost;
import app.bookey.domain.club.ClubPostRepository;
import app.bookey.domain.report.AbuseReportRepository;
import app.bookey.domain.review.Review;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** 신고 큐 처리 (§F13 신고 큐 · §8.3). SLA 48h. */
@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final ModerationTicketRepository ticketRepository;
    private final ReviewRepository reviewRepository;
    private final ClubPostRepository clubPostRepository;
    private final AbuseReportRepository abuseReportRepository;
    private final UserRepository userRepository;
    private final AdminUserService adminUserService;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ModerationRow> queue(ModerationStatus status, ModerationSource sourceType,
                                             int page, int size) {
        return PageResponse.of(
                ticketRepository.search(status, sourceType, PageRequest.of(page, size)),
                this::toRow);
    }

    private ModerationRow toRow(ModerationTicket ticket) {
        String preview = null;
        Long authorId = null;

        if (ticket.getSourceType() == ModerationSource.REVIEW) {
            Optional<Review> review = reviewRepository.findById(ticket.getSourceId());
            preview = review.map(r -> truncate(r.getBody())).orElse("(삭제됨)");
            authorId = review.map(Review::getUserId).orElse(null);
        } else if (ticket.getSourceType() == ModerationSource.CLUB_POST) {
            Optional<ClubPost> post = clubPostRepository.findById(ticket.getSourceId());
            preview = post.map(p -> truncate(p.getBody())).orElse("(삭제됨)");
            authorId = post.map(ClubPost::getUserId).orElse(null);
        }

        String authorNickname = authorId == null ? null : userRepository.findById(authorId)
                .map(User::getNickname).orElse(null);

        return new ModerationRow(ticket.getId(), ticket.getSourceType(), ticket.getSourceId(),
                ticket.getReason(), ticket.getReportCount(), ticket.getPriority(),
                ticket.getSlaDueAt(), ticket.isOverdue(), ticket.getStatus(),
                ticket.getAssignedAdminId(), preview, authorId, authorNickname);
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
                ticket.getSourceId(), request.note(), null,
                Map.of("resolution", request.resolution().name()));
    }

    /** @return 대상 콘텐츠 작성자 id */
    private Long applyResolution(ModerationTicket ticket, ModerationResolution resolution) {
        return switch (ticket.getSourceType()) {
            case REVIEW -> reviewRepository.findById(ticket.getSourceId()).map(review -> {
                switch (resolution) {
                    case KEEP -> review.restore();
                    case HIDE, SANCTION -> review.hide();
                    case DELETE -> review.softDelete();
                }
                return review.getUserId();
            }).orElse(null);
            case CLUB_POST -> clubPostRepository.findById(ticket.getSourceId()).map(post -> {
                switch (resolution) {
                    case KEEP -> post.restore();
                    case HIDE, SANCTION -> post.hide();
                    case DELETE -> post.softDelete();
                }
                return post.getUserId();
            }).orElse(null);
            default -> null;
        };
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

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}
