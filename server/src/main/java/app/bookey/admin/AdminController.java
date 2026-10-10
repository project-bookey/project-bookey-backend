package app.bookey.admin;

import app.bookey.admin.dto.AdminCatalogDtos.ClubCodeView;
import app.bookey.admin.dto.AdminDtos.*;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.*;
import app.bookey.domain.book.Book;
import app.bookey.domain.book.BookRepository;
import app.bookey.domain.club.*;
import app.bookey.domain.inquiry.InquiryRepository;
import app.bookey.domain.inquiry.InquiryStatus;
import app.bookey.domain.notification.NotificationRepository;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.review.Review;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.review.VerificationLevel;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import app.bookey.common.support.JoinCodeGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 백오피스 API (§F13). 서비스 JWT 로는 접근할 수 없다.
 * open-in-view 가 꺼져 있으므로 엔티티를 직접 고치는 핸들러는 @Transactional 이 있어야 변경이 저장된다 —
 * 없으면 감사 로그(별도 트랜잭션)만 남고 실제 값은 그대로다.
 */
@Tag(name = "Admin", description = "관리자 백오피스 — 대시보드 · 회원 · 도서 · 신고 · 모임 · 운영")
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
public class AdminController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 목록 한 쪽 최대 크기 — 실수로 size=100000 을 보내 DB 를 훑지 않게. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;
    private final AdminModerationService moderationService;
    private final AdminAuditService auditService;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final ReadingRecordRepository recordRepository;
    private final ReadingSessionRepository sessionRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubPostRepository clubPostRepository;
    private final NotificationRepository notificationRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final OpsFlagRepository opsFlagRepository;
    private final InquiryRepository inquiryRepository;
    private final AdminRepository adminRepository;
    private final app.bookey.api.club.ClubService clubService;

    // ── 대시보드 ─────────────────────────────────────────────
    @Operation(summary = "대시보드 KPI")
    @GetMapping("/dashboard")
    public DashboardView dashboard() {
        Instant dayStart = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long totalReviews = reviewRepository.countByStatus("VISIBLE");
        long verifiedReviews = reviewRepository.countByVerificationLevelInAndStatus(
                List.of(VerificationLevel.VERIFIED_FULL, VerificationLevel.VERIFIED_PARTIAL),
                "VISIBLE");
        long sent = notificationRepository.countSentSince(weekAgo);
        long converted = notificationRepository.countConvertedSince(weekAgo);

        return new DashboardView(
                userRepository.countByStatusNot(UserStatus.TERMINATED),
                sessionRepository.countActiveUsersSince(dayStart),
                sessionRepository.countSessionsSince(dayStart),
                recordRepository.countAllFinishedSince(dayStart),
                totalReviews == 0 ? 0 : (double) verifiedReviews / totalReviews,
                moderationService.pendingCount(),
                moderationService.overdueCount(),
                clubRepository.searchForAdminByStatus(null, ClubStatus.ACTIVE,
                        PageRequest.of(0, 1)).getTotalElements(),
                sent == 0 ? 0 : (double) converted / sent,
                inquiryRepository.countByStatus(InquiryStatus.WAITING));
    }

    // ── 회원 ────────────────────────────────────────────────
    @Operation(summary = "회원 검색")
    @GetMapping("/users")
    public PageResponse<UserRow> users(@AuthenticationPrincipal AuthAdmin admin,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) UserStatus status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return adminUserService.search(admin, keyword, status, page, pageSize(size));
    }

    @Operation(summary = "회원 상세 — revealReason 을 넣으면 이메일 전체가 보이고 열람 로그가 남는다")
    @GetMapping("/users/{userId}")
    public UserDetailView userDetail(@AuthenticationPrincipal AuthAdmin admin,
                                     @PathVariable Long userId,
                                     @RequestParam(required = false) String revealReason) {
        return adminUserService.detail(admin, userId, revealReason);
    }

    @Operation(summary = "회원 제재 — 사유 필수")
    @PostMapping("/users/{userId}/sanctions")
    public ResponseEntity<Void> sanction(@AuthenticationPrincipal AuthAdmin admin,
                                         @PathVariable Long userId,
                                         @Valid @RequestBody SanctionRequest request) {
        adminUserService.sanction(admin, userId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "제재 해제")
    @DeleteMapping("/users/{userId}/sanctions/{sanctionId}")
    public ResponseEntity<Void> releaseSanction(@AuthenticationPrincipal AuthAdmin admin,
                                                @PathVariable Long userId,
                                                @PathVariable Long sanctionId,
                                                @RequestParam String reason) {
        adminUserService.releaseSanction(admin, userId, sanctionId, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "구독 수동 지급 — 사유 필수 (IAP 검증 전 운영 경로)")
    @PostMapping("/users/{userId}/subscription")
    public ResponseEntity<Void> grantSubscription(@AuthenticationPrincipal AuthAdmin admin,
                                                  @PathVariable Long userId,
                                                  @Valid @RequestBody SubscriptionGrantRequest request) {
        adminUserService.grantSubscription(admin, userId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "지갑 수동 조정 — 책갈피·엽서·우표, 사유 필수 (베타 운영 경로)")
    @PostMapping("/users/{userId}/wallet")
    public ResponseEntity<Void> adjustWallet(@AuthenticationPrincipal AuthAdmin admin,
                                             @PathVariable Long userId,
                                             @Valid @RequestBody WalletAdjustRequest request) {
        adminUserService.adjustWallet(admin, userId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "구독 회수")
    @DeleteMapping("/users/{userId}/subscription")
    public ResponseEntity<Void> revokeSubscription(@AuthenticationPrincipal AuthAdmin admin,
                                                   @PathVariable Long userId,
                                                   @RequestParam String reason) {
        adminUserService.revokeSubscription(admin, userId, reason);
        return ResponseEntity.noContent().build();
    }

    // ── 도서 ────────────────────────────────────────────────
    @Operation(summary = "도서 검색")
    @GetMapping("/books")
    public PageResponse<BookRow> books(@RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                bookRepository.searchForAdmin(emptyToNull(keyword), PageRequest.of(page, pageSize(size))),
                AdminCatalogService::toRow);
    }

    @Operation(summary = "도서 메타 수정 — 페이지 수 보정 등")
    @PatchMapping("/books/{bookId}")
    @Transactional
    public ResponseEntity<Void> updateBook(@AuthenticationPrincipal AuthAdmin admin,
                                           @PathVariable Long bookId,
                                           @Valid @RequestBody UpdateBookRequest request) {
        if (!admin.role().canEditBook()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOK_NOT_FOUND));
        Map<String, Object> before = Map.of(
                "title", String.valueOf(book.getTitle()),
                "totalPages", String.valueOf(book.getTotalPages()));

        book.updateByAdmin(request.title(), request.author(), request.publisher(),
                request.totalPages(), request.coverUrl(), request.category());

        auditService.log(admin, "UPDATE_BOOK", "BOOK", bookId, request.reason(), before,
                Map.of("title", book.getTitle(),
                       "totalPages", String.valueOf(book.getTotalPages())));
        return ResponseEntity.noContent().build();
    }

    // ── 신고 큐 ─────────────────────────────────────────────
    @Operation(summary = "신고 큐 — SLA 48h, 우선순위 순")
    @GetMapping("/moderation")
    public PageResponse<ModerationRow> moderationQueue(
            @RequestParam(required = false) ModerationStatus status,
            @RequestParam(required = false) ModerationSource sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return moderationService.queue(status, sourceType, page, pageSize(size));
    }

    @Operation(summary = "신고 담당 지정")
    @PostMapping("/moderation/{ticketId}/assign")
    public ResponseEntity<Void> assign(@AuthenticationPrincipal AuthAdmin admin,
                                       @PathVariable Long ticketId) {
        moderationService.assign(admin, ticketId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "신고 처리 — 유지 / 숨김 / 삭제 / 제재")
    @PostMapping("/moderation/{ticketId}/resolve")
    public ResponseEntity<Void> resolve(@AuthenticationPrincipal AuthAdmin admin,
                                        @PathVariable Long ticketId,
                                        @Valid @RequestBody ResolveRequest request) {
        moderationService.resolve(admin, ticketId, request);
        return ResponseEntity.noContent().build();
    }

    // ── 검증 심사 ───────────────────────────────────────────
    @Operation(summary = "리뷰 목록 — 숨김·삭제 포함, 최근 순. 도서·회원·상태·검증 등급·신고 여부로 거른다")
    @GetMapping("/reviews")
    public PageResponse<ReviewRow> reviews(@RequestParam(required = false) Long bookId,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) VerificationLevel verificationLevel,
                                           @RequestParam(defaultValue = "false") boolean reportedOnly,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        var result = reviewRepository.findAll(
                ReviewRepository.adminSearch(bookId, userId, emptyToNull(status), verificationLevel, reportedOnly),
                PageRequest.of(page, pageSize(size), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(result, this::toReviewRow);
    }

    @Operation(summary = "검증 등급 수동 조정 — 사유 필수, 감사 로그 대상")
    @PostMapping("/reviews/{reviewId}/verification")
    @Transactional
    public ResponseEntity<Void> overrideVerification(
            @AuthenticationPrincipal AuthAdmin admin,
            @PathVariable Long reviewId,
            @Valid @RequestBody OverrideVerificationRequest request) {
        if (!admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        VerificationLevel before = review.getVerificationLevel();
        review.overrideVerification(request.level());

        auditService.log(admin, "OVERRIDE_VERIFICATION", "REVIEW", reviewId, request.reason(),
                Map.of("level", before.name()), Map.of("level", request.level().name()));
        return ResponseEntity.noContent().build();
    }

    // ── 모임 ────────────────────────────────────────────────
    @Operation(summary = "모임 목록")
    @GetMapping("/clubs")
    public PageResponse<ClubRow> clubs(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) ClubStatus status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        String normalized = emptyToNull(keyword);
        var pageable = PageRequest.of(page, pageSize(size));
        var clubPage = status == null
                ? clubRepository.searchForAdmin(normalized, pageable)
                : clubRepository.searchForAdminByStatus(normalized, status, pageable);
        return PageResponse.of(
                clubPage,
                club -> new ClubRow(club.getId(), club.getName(), club.getJoinCode(),
                        club.getStatus(), club.getMemberCount(), club.getMemberLimit(),
                        club.getStartsAt(), club.getEndsAt(), club.getOwnerId(),
                        userRepository.findById(club.getOwnerId()).map(User::getNickname).orElse(null),
                        clubPostRepository.countByClubIdAndStatus(club.getId(), "VISIBLE"),
                        club.getCreatedAt()));
    }

    @Operation(summary = "모임 강제 해산")
    @PostMapping("/clubs/{clubId}/force-end")
    @Transactional
    public ResponseEntity<Void> forceEndClub(@AuthenticationPrincipal AuthAdmin admin,
                                             @PathVariable Long clubId,
                                             @Valid @RequestBody ClubActionRequest request) {
        if (!admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        ClubStatus before = club.getStatus();
        clubService.adminEnd(clubId);   // 호스트가 끝낸 것과 같은 종료 기록(ClubEvent)을 남긴다
        auditService.log(admin, "FORCE_END_CLUB", "CLUB", clubId, request.reason(),
                Map.of("status", before.name(), "name", club.getName()),
                Map.of("status", club.getStatus().name()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "초대 코드 강제 회전")
    @PostMapping("/clubs/{clubId}/rotate-code")
    @Transactional
    public ClubCodeView rotateClubCode(@AuthenticationPrincipal AuthAdmin admin,
                                              @PathVariable Long clubId,
                                              @Valid @RequestBody ClubActionRequest request) {
        if (!admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        String previous = club.getJoinCode();
        String code;
        do {
            code = JoinCodeGenerator.generate();
        } while (clubRepository.existsByJoinCode(code));
        club.rotateJoinCode(code);
        auditService.log(admin, "ROTATE_CLUB_CODE", "CLUB", clubId, request.reason(),
                Map.of("joinCode", String.valueOf(previous)), Map.of("joinCode", code));
        return new ClubCodeView(code);
    }

    @Operation(summary = "호스트 승계 — 호스트 장기 미접속 대응")
    @PostMapping("/clubs/{clubId}/transfer-host")
    @Transactional
    public ResponseEntity<Void> transferHost(@AuthenticationPrincipal AuthAdmin admin,
                                             @PathVariable Long clubId,
                                             @RequestParam Long newOwnerId,
                                             @Valid @RequestBody ClubActionRequest request) {
        if (!admin.canModerate()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CLUB_NOT_FOUND));
        Long previousOwnerId = club.getOwnerId();
        // 정지·탈퇴 회원에게는 넘기지 않는다(ClubService 가 검사)
        clubService.adminTransferHost(clubId, newOwnerId);

        auditService.log(admin, "TRANSFER_CLUB_HOST", "CLUB", clubId, request.reason(),
                Map.of("ownerId", previousOwnerId), Map.of("ownerId", newOwnerId));
        return ResponseEntity.noContent().build();
    }

    // ── 알림 운영 ───────────────────────────────────────────
    @Operation(summary = "알림 발송 통계")
    @GetMapping("/notifications/stats")
    public NotificationStats notificationStats() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long sent = notificationRepository.countSentSince(weekAgo);
        long converted = notificationRepository.countConvertedSince(weekAgo);
        boolean pushEnabled = opsFlagRepository.findById(OpsFlag.PUSH_ENABLED)
                .map(OpsFlag::isEnabled).orElse(true);
        return new NotificationStats(sent, converted,
                sent == 0 ? 0 : (double) converted / sent, pushEnabled);
    }

    @Operation(summary = "운영 스위치 목록")
    @GetMapping("/ops-flags")
    public List<OpsFlagRow> opsFlags() {
        return opsFlagRepository.findAll().stream()
                .map(f -> new OpsFlagRow(f.getKey(), f.isEnabled(), f.getNote(), f.getUpdatedAt()))
                .toList();
    }

    @Operation(summary = "운영 스위치 변경 — PUSH_ENABLED 는 긴급 킬스위치 (SUPER_ADMIN)")
    @PatchMapping("/ops-flags/{key}")
    @Transactional
    public ResponseEntity<Void> updateOpsFlag(@AuthenticationPrincipal AuthAdmin admin,
                                              @PathVariable String key,
                                              @Valid @RequestBody OpsFlagRequest request) {
        if (!admin.role().canManageOps()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        OpsFlag flag = opsFlagRepository.findById(key)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        boolean before = flag.isEnabled();
        flag.change(request.enabled(), admin.id(), request.note());

        auditService.log(admin, "UPDATE_OPS_FLAG", "OPS_FLAG", null, request.note(),
                Map.of("key", key, "enabled", before),
                Map.of("key", key, "enabled", request.enabled()));
        return ResponseEntity.noContent().build();
    }

    // ── 감사 로그 ───────────────────────────────────────────
    @Operation(summary = "감사 로그 — 모든 관리자 행위 기록. from 이상 to 미만, 최신순")
    @GetMapping("/audit-logs")
    public PageResponse<AuditRow> auditLogs(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, pageSize(size), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var logs = auditLogRepository.findAll(AdminAuditLogRepository.matching(
                adminId, emptyToNull(action), emptyToNull(targetType), targetId, from, to), pageable);

        Map<Long, String> names = adminRepository.findAllById(
                        logs.getContent().stream().map(AdminAuditLog::getAdminId).distinct().toList())
                .stream().collect(Collectors.toMap(Admin::getId, Admin::getName));
        return PageResponse.of(logs,
                log -> new AuditRow(log.getId(), log.getAdminId(), log.getAction(),
                        log.getTargetType(), log.getTargetId(), log.getReason(), log.getIp(),
                        log.getCreatedAt(), names.get(log.getAdminId()),
                        log.getBeforeData(), log.getAfterData(), log.getUserAgent()));
    }

    private ReviewRow toReviewRow(Review review) {
        return new ReviewRow(review.getId(), review.getBookId(),
                bookRepository.findById(review.getBookId()).map(Book::getTitle).orElse(null),
                review.getUserId(),
                userRepository.findById(review.getUserId()).map(User::getNickname).orElse(null),
                review.getRating(), review.getBody(), review.getVerificationLevel(),
                review.getVerificationSnapshot(), review.getReportCount(),
                review.getStatus(), review.getCreatedAt());
    }

    private static int pageSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
