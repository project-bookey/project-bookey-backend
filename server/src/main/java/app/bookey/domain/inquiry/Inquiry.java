package app.bookey.domain.inquiry;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 고객문의(1:1). 답변은 같은 행에 담는다 — 문의 하나에 답변 하나이고, 더 물을 게 있으면 새 문의로 받는다.
 * 기기 정보(앱 버전·플랫폼·OS·모델)는 앱이 자동으로 붙여 보내는 값이라 비어 있을 수 있다.
 */
@Getter
@Entity
@Table(name = "inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Column(length = 20)
    private String platform;

    @Column(name = "os_version", length = 30)
    private String osVersion;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status = InquiryStatus.WAITING;

    @Column(columnDefinition = "text")
    private String answer;

    /** 마지막으로 답변을 저장한 관리자. 관리자 계정이 지워지면 비워진다. */
    @Column(name = "answered_by")
    private Long answeredBy;

    /** 첫 답변 시각 — 답변을 고쳐도 바뀌지 않는다. */
    @Column(name = "answered_at")
    private Instant answeredAt;

    /** 마지막으로 고친 시각 — 고친 적이 없으면 null. */
    @Column(name = "answer_updated_at")
    private Instant answerUpdatedAt;

    @Builder
    private Inquiry(Long userId, InquiryCategory category, String body,
                    String appVersion, String platform, String osVersion, String deviceModel) {
        this.userId = userId;
        this.category = category;
        this.body = body;
        this.appVersion = appVersion;
        this.platform = platform;
        this.osVersion = osVersion;
        this.deviceModel = deviceModel;
        this.status = InquiryStatus.WAITING;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 첫 답변. 이미 답한 문의면 거절한다 — 두 관리자가 동시에 답해 덮어쓰거나 알림이 두 번 가지 않게. */
    public void answer(Long adminId, String text, Instant now) {
        if (status != InquiryStatus.WAITING) {
            throw ApiException.of(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        this.answer = text;
        this.answeredBy = adminId;
        this.answeredAt = now;
        this.status = InquiryStatus.ANSWERED;
    }

    /** 답변 고치기. 첫 답변 시각은 그대로 두고 고친 시각만 남긴다. */
    public void editAnswer(Long adminId, String text, Instant now) {
        if (status != InquiryStatus.ANSWERED) {
            throw ApiException.of(ErrorCode.INQUIRY_NOT_ANSWERED);
        }
        this.answer = text;
        this.answeredBy = adminId;
        this.answerUpdatedAt = now;
    }
}
