package app.bookey.api.banner.dto;

import app.bookey.domain.banner.Banner;
import app.bookey.domain.banner.BannerKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class BannerDtos {
    private BannerDtos() {}

    /** 앱 홈에 노출되는 활성 배너. */
    public record BannerView(
            @NotNull Long id,
            @NotNull BannerKind kind,
            @NotNull String title,
            String subtitle,
            String imageUrl,
            String bgColor,
            String linkUrl,
            int sortOrder
    ) {
        public static BannerView from(Banner b) {
            return new BannerView(b.getId(), b.getKind(), b.getTitle(), b.getSubtitle(),
                    b.getImageUrl(), b.getBgColor(), b.getLinkUrl(), b.getSortOrder());
        }
    }

    /** 어드민 조회용 — 기간·활성 여부 포함. */
    public record BannerAdminView(
            @NotNull Long id,
            @NotNull BannerKind kind,
            @NotNull String title,
            String subtitle,
            String imageUrl,
            String bgColor,
            String linkUrl,
            int sortOrder,
            boolean enabled,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {
        public static BannerAdminView from(Banner b) {
            return new BannerAdminView(b.getId(), b.getKind(), b.getTitle(), b.getSubtitle(), b.getImageUrl(),
                    b.getBgColor(), b.getLinkUrl(), b.getSortOrder(), b.isEnabled(),
                    b.getStartsAt(), b.getEndsAt());
        }
    }

    /** 어드민 생성/수정 요청 — 전체 필드 교체. */
    public record BannerUpsertRequest(
            @NotNull BannerKind kind,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 200) String subtitle,
            @Size(max = 500) String imageUrl,
            @Size(max = 20) String bgColor,
            @Size(max = 500) String linkUrl,
            int sortOrder,
            boolean enabled,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt
    ) {}
}
