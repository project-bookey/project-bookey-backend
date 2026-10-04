package app.bookey.api.inquiry.dto;

import app.bookey.domain.inquiry.Inquiry;
import app.bookey.domain.inquiry.InquiryCategory;
import app.bookey.domain.inquiry.InquiryImage;
import app.bookey.domain.inquiry.InquiryRules;
import app.bookey.domain.inquiry.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class InquiryDtos {
    private InquiryDtos() {}

    /**
     * 문의 작성. 기기 정보(appVersion·platform·osVersion·deviceModel)는 앱이 자동으로 붙이는 값이라
     * 길이 검증으로 거절하지 않고 서버가 칸 길이에 맞춰 자른다. platform 은 웹이 있어 enum 이 아닌 문자열(IOS|ANDROID|WEB).
     */
    public record CreateInquiryRequest(
            @NotNull InquiryCategory category,
            @NotBlank @Size(max = InquiryRules.MAX_BODY) String body,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = InquiryRules.MAX_IMAGES, message = "사진은 {max}장까지 붙일 수 있어요.") List<Long> imageIds,
            String appVersion,
            String platform,
            String osVersion,
            String deviceModel
    ) {}

    public record InquiryImageView(@NotNull Long id, @NotNull String url, Integer width, Integer height) {
        public static InquiryImageView from(InquiryImage image) {
            return new InquiryImageView(image.getId(), image.getUrl(), image.getWidth(), image.getHeight());
        }
    }

    /** 작성 화면의 유형 칩 — 서버 순서 그대로, 맨 앞이 기본값. */
    public record InquiryCategoryView(@NotNull InquiryCategory code, @NotNull String label) {
        public static InquiryCategoryView from(InquiryCategory category) {
            return new InquiryCategoryView(category, category.getLabel());
        }
    }

    /** 내 문의 목록 한 줄. */
    public record InquirySummaryView(
            @NotNull Long id,
            @NotNull InquiryCategory category,
            @NotNull String categoryLabel,
            @NotNull String preview,
            @NotNull InquiryStatus status,
            @NotNull Instant createdAt,
            Instant answeredAt
    ) {
        public static InquirySummaryView from(Inquiry inquiry) {
            return new InquirySummaryView(inquiry.getId(), inquiry.getCategory(), inquiry.getCategory().getLabel(),
                    InquiryRules.preview(inquiry.getBody()), inquiry.getStatus(), inquiry.getCreatedAt(),
                    inquiry.getAnsweredAt());
        }
    }

    /** 문의 한 건과 답변. 답변한 관리자가 누구인지는 내려주지 않는다(앱은 '북키 답변'으로만 보여 준다). */
    public record InquiryView(
            @NotNull Long id,
            @NotNull InquiryCategory category,
            @NotNull String categoryLabel,
            @NotNull String body,
            @NotNull InquiryStatus status,
            List<InquiryImageView> images,
            String answer,
            Instant answeredAt,
            Instant answerUpdatedAt,
            @NotNull Instant createdAt
    ) {
        public static InquiryView from(Inquiry inquiry, List<InquiryImage> images) {
            return new InquiryView(inquiry.getId(), inquiry.getCategory(), inquiry.getCategory().getLabel(),
                    inquiry.getBody(), inquiry.getStatus(), images.stream().map(InquiryImageView::from).toList(),
                    inquiry.getAnswer(), inquiry.getAnsweredAt(), inquiry.getAnswerUpdatedAt(),
                    inquiry.getCreatedAt());
        }
    }
}
