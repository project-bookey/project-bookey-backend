package app.bookey.admin;

import app.bookey.admin.dto.AdminDtos.InquiryAdminView;
import app.bookey.admin.dto.AdminDtos.InquiryRow;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.admin.support.PrivacyMasker;
import app.bookey.api.inquiry.dto.InquiryDtos.InquiryImageView;
import app.bookey.api.notification.NotificationService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminRepository;
import app.bookey.domain.inquiry.Inquiry;
import app.bookey.domain.inquiry.InquiryCategory;
import app.bookey.domain.inquiry.InquiryImage;
import app.bookey.domain.inquiry.InquiryImageRepository;
import app.bookey.domain.inquiry.InquiryRepository;
import app.bookey.domain.inquiry.InquiryRules;
import app.bookey.domain.inquiry.InquiryStatus;
import app.bookey.domain.notification.NotificationType;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 고객문의 답변 (어드민). 보기는 모든 관리자, 답변은 {@code canHandleSupport}(VIEWER 제외).
 * 첫 답변만 사용자에게 알린다 — 고치는 건 대개 오탈자라 다시 알리지 않고 앱에 '수정됨'만 보인다.
 */
@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    static final int MAX_PAGE_SIZE = 50;
    /** 감사 로그에 남기는 답변 길이 — 로그 행이 커지지 않게. */
    private static final int AUDIT_TEXT_LENGTH = 1000;
    private static final String UNKNOWN_USER = "(알 수 없음)";

    private final InquiryRepository inquiryRepository;
    private final InquiryImageRepository imageRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<InquiryRow> list(InquiryStatus status, InquiryCategory category, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                InquiryRules.adminSort(status));
        Page<Inquiry> inquiries;
        if (status != null && category != null) {
            inquiries = inquiryRepository.findAllByStatusAndCategory(status, category, pageable);
        } else if (status != null) {
            inquiries = inquiryRepository.findAllByStatus(status, pageable);
        } else if (category != null) {
            inquiries = inquiryRepository.findAllByCategory(category, pageable);
        } else {
            inquiries = inquiryRepository.findAllBy(pageable);
        }

        // 한 페이지의 회원과 첨부 수를 각각 한 번에 읽는다 — 줄마다 조회하지 않게.
        List<Long> userIds = inquiries.getContent().stream().map(Inquiry::getUserId).distinct().toList();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> inquiryIds = inquiries.getContent().stream().map(Inquiry::getId).toList();
        Map<Long, Long> imageCounts = inquiryIds.isEmpty() ? Map.of() : imageRepository.findAllByInquiryIdIn(inquiryIds)
                .stream().collect(Collectors.groupingBy(InquiryImage::getInquiryId, Collectors.counting()));

        return PageResponse.of(inquiries, inquiry -> {
            User user = users.get(inquiry.getUserId());
            return new InquiryRow(inquiry.getId(), inquiry.getCategory(), inquiry.getStatus(),
                    InquiryRules.preview(inquiry.getBody()),
                    imageCounts.getOrDefault(inquiry.getId(), 0L).intValue(),
                    inquiry.getUserId(),
                    user == null ? UNKNOWN_USER : user.getNickname(),
                    user == null ? UNKNOWN_USER : user.getHandle(),
                    inquiry.getCreatedAt(), inquiry.getAnsweredAt());
        });
    }

    /**
     * 문의 본문은 개인정보라 열람할 때마다 감사 로그(VIEW_INQUIRY)를 남긴다.
     * 읽기 전용 트랜잭션으로 두면 그 안에서 남기는 감사 로그 INSERT 가 막힌다(회원 상세와 같은 이유로 쓰기 트랜잭션).
     */
    @Transactional
    public InquiryAdminView detail(AuthAdmin admin, Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> ApiException.of(ErrorCode.INQUIRY_NOT_FOUND));
        auditService.logView(admin, "INQUIRY", inquiryId);
        return toView(inquiry);
    }

    @Transactional
    public InquiryAdminView answer(AuthAdmin admin, Long inquiryId, String text) {
        requireSupport(admin);
        Inquiry inquiry = lock(inquiryId);
        String answer = text.strip();
        inquiry.answer(admin.id(), answer, clock.instant());
        // 잠금화면에 문의 내용이 보이지 않게 알림 문구는 유형만 담는다.
        notificationService.inApp(new NotificationService.NotificationRequest(
                inquiry.getUserId(), NotificationType.INQUIRY_ANSWERED, null, null, null,
                "문의하신 내용에 답변이 도착했어요",
                "'" + inquiry.getCategory().getLabel() + "' 문의에 답변이 등록됐어요.",
                Map.of("inquiryId", inquiry.getId()), null));
        auditService.log(admin, "ANSWER_INQUIRY", "INQUIRY", inquiryId, null, null,
                Map.of("answer", truncate(answer)));
        return toView(inquiry);
    }

    @Transactional
    public InquiryAdminView editAnswer(AuthAdmin admin, Long inquiryId, String text) {
        requireSupport(admin);
        Inquiry inquiry = lock(inquiryId);
        Map<String, Object> before = new HashMap<>();
        before.put("answer", truncate(inquiry.getAnswer()));
        String answer = text.strip();
        inquiry.editAnswer(admin.id(), answer, clock.instant());
        auditService.log(admin, "EDIT_INQUIRY_ANSWER", "INQUIRY", inquiryId, null, before,
                Map.of("answer", truncate(answer)));
        return toView(inquiry);
    }

    /** 사용자가 그사이 문의를 지웠으면 INQUIRY_NOT_FOUND — 어드민은 '사용자가 삭제한 문의'로 보여 준다. */
    private Inquiry lock(Long inquiryId) {
        return inquiryRepository.findByIdForUpdate(inquiryId)
                .orElseThrow(() -> ApiException.of(ErrorCode.INQUIRY_NOT_FOUND));
    }

    private InquiryAdminView toView(Inquiry inquiry) {
        User user = userRepository.findById(inquiry.getUserId()).orElse(null);
        String answeredByName = inquiry.getAnsweredBy() == null ? null
                : adminRepository.findById(inquiry.getAnsweredBy()).map(Admin::getName).orElse(null);
        List<InquiryImageView> images = imageRepository.findAllByInquiryIdOrderBySortOrderAscIdAsc(inquiry.getId())
                .stream().map(InquiryImageView::from).toList();
        return new InquiryAdminView(inquiry.getId(), inquiry.getCategory(), inquiry.getStatus(), inquiry.getBody(),
                images, inquiry.getAppVersion(), inquiry.getPlatform(), inquiry.getOsVersion(), inquiry.getDeviceModel(),
                inquiry.getUserId(),
                user == null ? UNKNOWN_USER : user.getNickname(),
                user == null ? UNKNOWN_USER : user.getHandle(),
                user == null ? null : PrivacyMasker.email(user.getEmail()),
                user == null ? null : user.getStatus(),
                inquiry.getAnswer(), answeredByName, inquiry.getAnsweredAt(), inquiry.getAnswerUpdatedAt(),
                inquiry.getCreatedAt());
    }

    private static void requireSupport(AuthAdmin admin) {
        if (!admin.role().canHandleSupport()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }

    /** 코드포인트 기준으로 자른다 — 이모지가 반으로 갈리지 않게. */
    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.codePointCount(0, text.length()) <= AUDIT_TEXT_LENGTH
                ? text
                : text.substring(0, text.offsetByCodePoints(0, AUDIT_TEXT_LENGTH));
    }
}
