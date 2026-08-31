package app.bookey.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** application.yml 의 bookey.* 설정. */
@ConfigurationProperties(prefix = "bookey")
public record BookeyProperties(
        Jwt jwt,
        BookApi bookApi,
        Club club,
        Notification notification
) {
    public record Jwt(
            String secret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Duration adminTokenTtl
    ) {}

    public record BookApi(
            String kakaoKey,
            String aladinTtbKey,
            String googleBooksKey,
            Duration cacheTtl
    ) {}

    public record Club(
            int defaultMemberLimit,
            int maxMemberLimit,
            Duration nudgeCooldown,
            int nudgeDailyLimit,
            int joinCodeLookupRateLimit
    ) {}

    public record Notification(
            int dailyCap,
            int weeklyCap,
            int clubDailyCap,
            int defaultSendHour
    ) {}
}
