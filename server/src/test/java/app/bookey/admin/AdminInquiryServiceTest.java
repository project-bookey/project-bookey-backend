package app.bookey.admin;

import app.bookey.admin.dto.AdminDtos.InquiryAdminView;
import app.bookey.admin.dto.AdminDtos.InquiryRow;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.api.notification.NotificationService;
import app.bookey.api.notification.NotificationService.NotificationRequest;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.admin.AdminRole;
import app.bookey.domain.inquiry.Inquiry;
import app.bookey.domain.inquiry.InquiryCategory;
import app.bookey.domain.inquiry.InquiryImageRepository;
import app.bookey.domain.inquiry.InquiryRepository;
import app.bookey.domain.inquiry.InquiryStatus;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminInquiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T03:00:00Z");
    private static final AuthAdmin SUPPORT = new AuthAdmin(3L, "cs@bookey.app", AdminRole.SUPPORT);
    private static final AuthAdmin VIEWER = new AuthAdmin(4L, "view@bookey.app", AdminRole.VIEWER);

    private final InquiryRepository inquiryRepository = mock(InquiryRepository.class);
    private final InquiryImageRepository imageRepository = mock(InquiryImageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final AdminInquiryService service = new AdminInquiryService(inquiryRepository, imageRepository,
            userRepository, adminRepository, notificationService, auditService, Clock.fixed(NOW, ZoneOffset.UTC));

    private static Inquiry inquiry(long id, long userId) {
        Inquiry inquiry = Inquiry.builder().userId(userId).category(InquiryCategory.BUG).body("앱이 꺼져요\n자주요").build();
        set(inquiry, "id", id);
        set(inquiry, "createdAt", Instant.parse("2026-10-02T00:00:00Z"));
        return inquiry;
    }

    private static User user(long id) {
        User user = User.builder().handle("reader" + id).email("reader@dev.local").nickname("독자").build();
        set(user, "id", id);
        return user;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("첫 답변은 사용자에게 INQUIRY_ANSWERED 알림을 한 번 보내고(본문 없이 유형만) 감사 로그를 남긴다")
    void answerNotifiesOnceAndAudits() {
        Inquiry inquiry = inquiry(5L, 10L);
        when(inquiryRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inquiry));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L)));

        InquiryAdminView view = service.answer(SUPPORT, 5L, "  확인 후 고쳤어요  ");

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(inquiry.getAnswer()).isEqualTo("확인 후 고쳤어요");
        assertThat(inquiry.getAnsweredAt()).isEqualTo(NOW);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).inApp(sent.capture());
        assertThat(sent.getValue().userId()).isEqualTo(10L);
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.INQUIRY_ANSWERED);
        assertThat(sent.getValue().payload()).containsEntry("inquiryId", 5L);
        assertThat(sent.getValue().body()).doesNotContain("앱이 꺼져요");
        verify(auditService).log(eq(SUPPORT), eq("ANSWER_INQUIRY"), eq("INQUIRY"), eq(5L), isNull(), isNull(), anyMap());
        assertThat(view.maskedEmail()).isEqualTo("re***@dev.local");
    }

    @Test
    @DisplayName("답변 수정은 알림을 다시 보내지 않고 고치기 전후를 감사 로그에 남긴다")
    void editDoesNotNotify() {
        Inquiry inquiry = inquiry(5L, 10L);
        inquiry.answer(3L, "첫 답변", NOW.minusSeconds(60));
        when(inquiryRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inquiry));

        service.editAnswer(SUPPORT, 5L, "고친 답변");

        assertThat(inquiry.getAnswer()).isEqualTo("고친 답변");
        assertThat(inquiry.getAnswerUpdatedAt()).isEqualTo(NOW);
        verify(notificationService, never()).inApp(any());
        verify(auditService).log(eq(SUPPORT), eq("EDIT_INQUIRY_ANSWER"), eq("INQUIRY"), eq(5L), isNull(),
                eq(Map.of("answer", "첫 답변")), eq(Map.of("answer", "고친 답변")));
    }

    @Test
    @DisplayName("보기 전용(VIEWER)은 답변할 수 없다 — 문의를 잠그기 전에 거절한다")
    void viewerCannotAnswer() {
        assertThatThrownBy(() -> service.answer(VIEWER, 5L, "답변"))
                .extracting("errorCode").isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
        verify(inquiryRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("사용자가 그사이 지운 문의에 답하면 INQUIRY_NOT_FOUND — 알림도 가지 않는다")
    void answerDeletedInquiry() {
        when(inquiryRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.answer(SUPPORT, 5L, "답변"))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        verify(notificationService, never()).inApp(any());
    }

    @Test
    @DisplayName("상세를 열면 열람 로그(VIEW_INQUIRY)를 남긴다")
    void detailLogsView() {
        when(inquiryRepository.findById(5L)).thenReturn(Optional.of(inquiry(5L, 10L)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L)));

        InquiryAdminView view = service.detail(SUPPORT, 5L);

        verify(auditService).logView(SUPPORT, "INQUIRY", 5L);
        assertThat(view.userHandle()).isEqualTo("reader10");
        assertThat(view.body()).isEqualTo("앱이 꺼져요\n자주요");
    }

    @Test
    @DisplayName("목록은 회원과 첨부 수를 한 번에 읽어 채우고, 미리보기는 한 줄로 접는다")
    void listFillsUsersAndImageCounts() {
        Inquiry inquiry = inquiry(5L, 10L);
        when(inquiryRepository.findAllByStatus(eq(InquiryStatus.WAITING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inquiry)));
        when(userRepository.findAllById(List.of(10L))).thenReturn(List.of(user(10L)));
        when(imageRepository.findAllByInquiryIdIn(List.of(5L))).thenReturn(List.of());

        PageResponse<InquiryRow> page = service.list(InquiryStatus.WAITING, null, 0, 20);

        InquiryRow row = page.content().getFirst();
        assertThat(row.userNickname()).isEqualTo("독자");
        assertThat(row.imageCount()).isZero();
        assertThat(row.preview()).isEqualTo("앱이 꺼져요 자주요");
    }
}
