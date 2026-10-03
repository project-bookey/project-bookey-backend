package app.bookey.api.faq.dto;

import app.bookey.domain.faq.Faq;
import app.bookey.domain.inquiry.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class FaqDtos {
    private FaqDtos() {}

    /** 앱에 보이는 FAQ — 노출 중인 것만, 정렬 순. 앱은 분류(categoryLabel)로 묶어 보여 준다. */
    public record FaqView(
            @NotNull Long id,
            @NotNull InquiryCategory category,
            @NotNull String categoryLabel,
            @NotNull String question,
            @NotNull String answer
    ) {
        public static FaqView from(Faq faq) {
            return new FaqView(faq.getId(), faq.getCategory(), faq.getCategory().getLabel(),
                    faq.getQuestion(), faq.getAnswer());
        }
    }

    /** 어드민 조회용 — 숨긴 FAQ 와 순서 포함. */
    public record FaqAdminView(
            @NotNull Long id,
            @NotNull InquiryCategory category,
            @NotNull String question,
            @NotNull String answer,
            int sortOrder,
            boolean visible,
            @NotNull Instant createdAt,
            Instant updatedAt
    ) {
        public static FaqAdminView from(Faq faq) {
            return new FaqAdminView(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer(),
                    faq.getSortOrder(), faq.isVisible(), faq.getCreatedAt(), faq.getUpdatedAt());
        }
    }

    /** 어드민 생성/수정 — 전체 필드 교체. 순서는 여기서 받지 않고 순서 바꾸기로만 정한다. */
    public record FaqUpsertRequest(
            @NotNull InquiryCategory category,
            @NotBlank @Size(max = 200) String question,
            @NotBlank @Size(max = 5000) String answer,
            boolean visible
    ) {}

    /** 순서 바꾸기 — 지금 있는 FAQ 전체 id 를 원하는 순서대로. */
    public record FaqOrderRequest(@NotEmpty List<@NotNull Long> ids) {}
}
