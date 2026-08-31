package app.bookey.admin;

import app.bookey.admin.dto.AdminDtos.*;
import app.bookey.admin.support.AdminAuditService;
import app.bookey.admin.support.PrivacyMasker;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.admin.SanctionType;
import app.bookey.domain.admin.UserSanction;
import app.bookey.domain.admin.UserSanctionRepository;
import app.bookey.domain.club.ClubMemberRepository;
import app.bookey.domain.club.ClubMemberStatus;
import app.bookey.domain.reading.ReadingRecordRepository;
import app.bookey.domain.reading.ReadingSessionRepository;
import app.bookey.domain.reading.ReadingStatus;
import app.bookey.domain.review.ReviewRepository;
import app.bookey.domain.user.User;
import app.bookey.domain.user.UserRepository;
import app.bookey.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final ReadingRecordRepository recordRepository;
    private final ReadingSessionRepository sessionRepository;
    private final ReviewRepository reviewRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final UserSanctionRepository sanctionRepository;
    private final AdminAuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserRow> search(AuthAdmin admin, String keyword, UserStatus status,
                                        int page, int size) {
        String normalized = emptyToNull(keyword);
        var pageable = PageRequest.of(page, size);
        var result = status == null
                ? userRepository.searchByKeyword(normalized, pageable)
                : userRepository.searchByKeywordAndStatus(normalized, status, pageable);
        return PageResponse.of(result, user -> new UserRow(
                user.getId(), user.getHandle(), user.getNickname(),
                PrivacyMasker.email(user.getEmail()), user.getStatus(), user.getCreatedAt(),
                recordRepository.countByUserIdAndStatus(user.getId(), ReadingStatus.READING),
                recordRepository.countByUserIdAndStatus(user.getId(), ReadingStatus.FINISHED)));
    }

    /**
     * 회원 상세. 이메일 전체 보기는 사유를 남겨야 하며, 그 자체가 감사 로그 대상이다(§F13).
     */
    @Transactional
    public UserDetailView detail(AuthAdmin admin, Long userId, String revealReason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        boolean reveal = revealReason != null && !revealReason.isBlank();
        auditService.log(admin, reveal ? "VIEW_USER_PII" : "VIEW_USER", "USER", userId,
                reveal ? revealReason : null, null, null);

        List<SanctionRow> sanctions = sanctionRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> new SanctionRow(s.getId(), s.getType(), s.getReason(), s.getStartsAt(),
                        s.getEndsAt(), s.getReleasedAt(), s.getAdminId()))
                .toList();

        return new UserDetailView(
                user.getId(), user.getHandle(), user.getNickname(),
                reveal ? user.getEmail() : PrivacyMasker.email(user.getEmail()),
                user.getStatus(), user.getCreatedAt(),
                sessionRepository.countByUserId(userId),
                sessionRepository.sumDurationSecByUserSince(userId, Instant.EPOCH),
                reviewRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, "VISIBLE",
                        PageRequest.of(0, 1)).getTotalElements(),
                clubMemberRepository.findAllByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE).size(),
                sanctions);
    }

    @Transactional
    public void sanction(AuthAdmin admin, Long userId, SanctionRequest request) {
        if (!admin.canSanction() && request.type() != SanctionType.WARN) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.of(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        UserStatus before = user.getStatus();
        Instant endsAt = request.durationDays() == null
                ? null
                : Instant.now().plus(request.durationDays(), ChronoUnit.DAYS);

        sanctionRepository.save(new UserSanction(
                userId, admin.id(), request.type(), request.reason(), endsAt));

        UserStatus after = switch (request.type()) {
            case WARN -> before;
            case WRITE_BAN -> UserStatus.WRITE_BANNED;
            case SUSPEND -> UserStatus.SUSPENDED;
            case TERMINATE -> UserStatus.TERMINATED;
        };
        user.changeStatus(after);

        auditService.log(admin, "SANCTION_" + request.type().name(), "USER", userId,
                request.reason(),
                Map.of("status", before.name()),
                Map.of("status", after.name(), "endsAt", String.valueOf(endsAt)));
    }

    @Transactional
    public void releaseSanction(AuthAdmin admin, Long userId, Long sanctionId, String reason) {
        if (!admin.canSanction()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
        UserSanction sanction = sanctionRepository.findById(sanctionId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        sanction.release();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        boolean stillSanctioned = sanctionRepository.findAllByUserIdAndReleasedAtIsNull(userId)
                .stream()
                .anyMatch(UserSanction::isActive);
        if (!stillSanctioned) {
            user.changeStatus(UserStatus.ACTIVE);
        }
        auditService.log(admin, "RELEASE_SANCTION", "USER", userId, reason, null, null);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
