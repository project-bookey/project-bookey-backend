package app.bookey.api.report;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.admin.ModerationStatus;
import app.bookey.domain.admin.ModerationTicket;
import app.bookey.domain.admin.ModerationTicketRepository;
import app.bookey.domain.report.AbuseReport;
import app.bookey.domain.report.AbuseReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수 — 어떤 콘텐츠든 같은 규칙으로 받는다(리뷰·모임 글에 따로 있던 코드를 모았다).
 *  - 같은 사람이 같은 대상을 두 번 신고할 수 없다(CONFLICT).
 *  - 대상마다 신고 큐 티켓이 하나다. 이미 처리한 티켓에 새 신고가 들어오면 다시 연다 — '유지' 판정이 새 신고를 묻지 않게.
 *  - 티켓의 신고 수는 아직 처리하지 않은 신고 건수다. 3건이 넘으면 호출하는 쪽이 임시로 숨긴다({@link ModerationTicket#shouldAutoHide}).
 * 새 신고 경로(독후감·댓글·한줄평·회원)를 열 때도 이 서비스를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AbuseReportService {

    private static final String PENDING = "PENDING";

    private final AbuseReportRepository reportRepository;
    private final ModerationTicketRepository ticketRepository;

    @Transactional
    public ModerationTicket file(ModerationSource source, Long targetId, Long reporterId, String reason, String detail) {
        String type = source.name();
        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterId(type, targetId, reporterId)) {
            throw ApiException.of(ErrorCode.CONFLICT);
        }
        reportRepository.save(new AbuseReport(type, targetId, reporterId, reason, detail));

        ModerationTicket ticket = ticketRepository.findBySourceTypeAndSourceId(source, targetId)
                .orElseGet(() -> ticketRepository.save(new ModerationTicket(source, targetId, reason)));
        if (ticket.getStatus() == ModerationStatus.RESOLVED) {
            ticket.reopen(reason);
        }
        ticket.syncReportCount((int) reportRepository.countByTargetTypeAndTargetIdAndStatus(type, targetId, PENDING));
        return ticket;
    }
}
