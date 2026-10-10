package app.bookey.admin.dto;

import app.bookey.admin.dto.AdminDtos.ModerationRow;
import app.bookey.admin.dto.AdminDtos.SanctionRow;
import app.bookey.domain.admin.ModerationSource;
import app.bookey.domain.user.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 관리자 콘텐츠 검수와 신고 상세. */
public final class AdminContentDtos {

    private AdminContentDtos() {}

    /**
     * 관리자 조치. 독후감·리뷰·모임 글은 숨김·복구·삭제(행은 남김)를, 댓글·한줄평은 상태가 없어 삭제(완전 삭제)만 된다.
     * 무엇이 되는지는 각 행의 supportedActions 에 실린다.
     */
    public enum ContentAction { HIDE, RESTORE, DELETE }

    public record AdminContentRow(
            @NotNull ModerationSource type,
            @NotNull Long id,
            Long authorId,
            String authorNickname,
            UserStatus authorStatus,
            /** 독후감 제목. 다른 종류는 null. */
            String title,
            @NotNull String preview,
            /** VISIBLE | HIDDEN | DELETED. 댓글·한줄평은 늘 VISIBLE. */
            @NotNull String status,
            /** 독후감 공개 범위(PUBLIC·LINK·PRIVATE·CLUB). 다른 종류는 null. */
            String visibility,
            long reportCount,
            /** 어디에 달린 글인지 — 책 제목, 모임 이름, 부모 글 제목. */
            String contextLabel,
            Long bookId,
            Long clubId,
            /** 댓글이 달린 글(독후감·리뷰) 또는 답글의 부모. */
            Long parentId,
            @NotNull Instant createdAt,
            @NotNull List<ContentAction> supportedActions
    ) {}

    public record AdminContentDetail(
            @NotNull AdminContentRow content,
            /** 전문(독후감은 마크다운 그대로). */
            @NotNull String body,
            String imageUrl,
            /** 이 콘텐츠의 신고 큐 티켓. 없으면 null. */
            Long ticketId
    ) {}

    public record ContentActionRequest(
            @NotNull ContentAction action,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record AbuseReportRow(
            @NotNull Long id,
            @NotNull String targetType,
            @NotNull Long targetId,
            @NotNull Long reporterId,
            String reporterNickname,
            @NotNull String reason,
            String detail,
            @NotNull String status,
            @NotNull Instant createdAt
    ) {}

    /** 신고 큐 상세 — 신고 하나하나와 원문, 작성자의 제재 이력을 함께 본다. */
    public record ModerationDetailView(
            @NotNull ModerationRow ticket,
            /** 콘텐츠 신고면 원문. 모임·회원 신고거나 이미 지워졌으면 null. */
            AdminContentDetail content,
            @NotNull List<AbuseReportRow> reports,
            /** 작성자의 제재 이력 — 반복 위반인지 판단할 때. */
            @NotNull List<SanctionRow> authorSanctions
    ) {}
}
