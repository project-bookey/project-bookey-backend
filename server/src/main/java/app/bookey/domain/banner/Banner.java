package app.bookey.domain.banner;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 홈 이벤트 배너. 기간(startsAt~endsAt) 안에서만 노출한다. */
@Getter
@Entity
@Table(name = "banners")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "bg_color", length = 20)
    private String bgColor;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Builder
    private Banner(String title, String subtitle, String imageUrl, String bgColor,
                   String linkUrl, int sortOrder, boolean enabled, Instant startsAt, Instant endsAt) {
        this.title = title;
        this.subtitle = subtitle;
        this.imageUrl = imageUrl;
        this.bgColor = bgColor;
        this.linkUrl = linkUrl;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /** 시작 포함, 종료 제외의 반열린 구간. */
    public boolean isActiveAt(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    public void update(String title, String subtitle, String imageUrl, String bgColor,
                       String linkUrl, int sortOrder, boolean enabled, Instant startsAt, Instant endsAt) {
        this.title = title;
        this.subtitle = subtitle;
        this.imageUrl = imageUrl;
        this.bgColor = bgColor;
        this.linkUrl = linkUrl;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
}
