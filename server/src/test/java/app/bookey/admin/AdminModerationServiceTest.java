package app.bookey.admin;

import app.bookey.admin.dto.AdminDtos.ModerationRow;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.admin.ModerationTicket;
import app.bookey.domain.admin.ModerationTicketRepository;
import app.bookey.domain.admin.UserSanctionRepository;
import app.bookey.domain.report.AbuseReportRepository;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationServiceTest {

    private static final AuthAdmin VIEWER = new AuthAdmin(3L, "view@bookey.app", AdminRole.VIEWER);

    private final ModerationTicketRepository ticketRepository = mock(ModerationTicketRepository.class);
    private final AdminContentService contentService = mock(AdminContentService.class);
    private final AdminModerationService service = new AdminModerationService(ticketRepository,
            mock(AbuseReportRepository.class), mock(UserRepository.class), mock(AdminRepository.class),
            mock(UserSanctionRepository.class), mock(AdminUserService.class), contentService,
            mock(AdminAuditService.class));

    @Test
    @DisplayName("담당자가 없는 티켓도 목록에 그린다 — 지워진 대상은 '(삭제됨)'")
    void queueWithUnassignedTicket() {
        ModerationTicket ticket = new ModerationTicket(ModerationSource.REVIEW, 5L, "욕설");
        when(ticketRepository.findAllBy(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ticket)));
        when(contentService.summarize(ModerationSource.REVIEW, 5L)).thenReturn(Optional.empty());

        ModerationRow row = service.queue(null, null, 0, 20).content().getFirst();

        assertThat(row.assignedAdminName()).isNull();
        assertThat(row.contentPreview()).isEqualTo("(삭제됨)");
    }

    @Test
    @DisplayName("신고 상세는 경고 권한(CS 담당)부터 볼 수 있다")
    void detailNeedsWarn() {
        assertThatThrownBy(() -> service.detail(VIEWER, 1L))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }
}
