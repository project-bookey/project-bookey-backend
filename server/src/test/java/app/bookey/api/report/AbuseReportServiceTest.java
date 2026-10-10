package app.bookey.api.report;

import app.bookey.common.error.ErrorCode;
import app.bookey.domain.admin.ModerationResolution;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.admin.ModerationStatus;
import app.bookey.domain.admin.ModerationTicket;
import app.bookey.domain.admin.ModerationTicketRepository;
import app.bookey.domain.report.AbuseReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbuseReportServiceTest {

    private final AbuseReportRepository reportRepository = mock(AbuseReportRepository.class);
    private final ModerationTicketRepository ticketRepository = mock(ModerationTicketRepository.class);
    private final AbuseReportService service = new AbuseReportService(reportRepository, ticketRepository);

    @Test
    @DisplayName("같은 사람이 같은 대상을 두 번 신고하면 CONFLICT — 신고도 티켓도 만들지 않는다")
    void duplicateRejected() {
        when(reportRepository.existsByTargetTypeAndTargetIdAndReporterId("REVIEW", 5L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.file(ModerationSource.REVIEW, 5L, 7L, "욕설", null))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(reportRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("처리 전 신고가 3건이 되면 우선순위를 올리고 임시 숨김 대상이 된다")
    void threePendingReportsAutoHide() {
        ModerationTicket ticket = new ModerationTicket(ModerationSource.POST, 5L, "스팸");
        when(ticketRepository.findBySourceTypeAndSourceId(ModerationSource.POST, 5L)).thenReturn(Optional.of(ticket));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus("POST", 5L, "PENDING")).thenReturn(3L);

        ModerationTicket filed = service.file(ModerationSource.POST, 5L, 7L, "스팸", "광고 링크");

        assertThat(filed.getReportCount()).isEqualTo(3);
        assertThat(filed.getPriority()).isEqualTo((short) 1);
        assertThat(filed.shouldAutoHide()).isTrue();
    }

    @Test
    @DisplayName("이미 '유지' 로 처리한 대상에 새 신고가 오면 티켓을 다시 열고 새 신고만 센다")
    void resolvedTicketReopens() {
        ModerationTicket ticket = new ModerationTicket(ModerationSource.REVIEW, 5L, "욕설");
        ticket.resolve(1L, ModerationResolution.KEEP, "문제 없음");
        when(ticketRepository.findBySourceTypeAndSourceId(ModerationSource.REVIEW, 5L)).thenReturn(Optional.of(ticket));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus("REVIEW", 5L, "PENDING")).thenReturn(1L);

        ModerationTicket filed = service.file(ModerationSource.REVIEW, 5L, 8L, "스포일러", null);

        assertThat(filed.getStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(filed.getResolution()).isNull();
        assertThat(filed.getAssignedAdminId()).isNull();
        assertThat(filed.getReason()).isEqualTo("스포일러");
        assertThat(filed.getReportCount()).isEqualTo(1);
        assertThat(filed.shouldAutoHide()).isFalse();
    }

    @Test
    @DisplayName("첫 신고면 티켓을 새로 만든다")
    void firstReportCreatesTicket() {
        when(ticketRepository.findBySourceTypeAndSourceId(ModerationSource.BOOK_REMARK, 9L)).thenReturn(Optional.empty());
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus("BOOK_REMARK", 9L, "PENDING")).thenReturn(1L);

        ModerationTicket filed = service.file(ModerationSource.BOOK_REMARK, 9L, 7L, "비방", null);

        assertThat(filed.getSourceType()).isEqualTo(ModerationSource.BOOK_REMARK);
        assertThat(filed.getReportCount()).isEqualTo(1);
    }
}
