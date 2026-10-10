package app.bookey.domain.appconfig;

import app.bookey.domain.user.DevicePlatform;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 플랫폼별 앱 버전 안내. 행은 마이그레이션이 만들고(IOS·ANDROID), 관리자는 고치기만 한다. */
@Getter
@Entity
@Table(name = "app_release_configs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppReleaseConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DevicePlatform platform;

    @Column(name = "min_supported_version", nullable = false, length = 20)
    private String minSupportedVersion;

    @Column(name = "latest_version", nullable = false, length = 20)
    private String latestVersion;

    @Column(name = "store_url", length = 500)
    private String storeUrl;

    @Column(name = "update_message", length = 300)
    private String updateMessage;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void update(String minSupportedVersion, String latestVersion, String storeUrl, String updateMessage,
                       Long adminId, Instant now) {
        this.minSupportedVersion = minSupportedVersion;
        this.latestVersion = latestVersion;
        this.storeUrl = storeUrl;
        this.updateMessage = updateMessage;
        this.updatedBy = adminId;
        this.updatedAt = now;
    }

    /** 이 버전은 더 쓸 수 없다 — 앱이 업데이트 전에는 넘어가지 못하게 막는다. */
    public boolean requiresUpdate(String appVersion) {
        return AppVersion.isBelow(appVersion, minSupportedVersion);
    }

    /** 새 버전이 있다 — 권하기만 한다. */
    public boolean recommendsUpdate(String appVersion) {
        return AppVersion.isBelow(appVersion, latestVersion);
    }
}
