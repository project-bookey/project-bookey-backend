package app.bookey.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** application.yml 의 bookey.* 설정. */
@ConfigurationProperties(prefix = "bookey")
public record BookeyProperties(
        Jwt jwt,
        BookApi bookApi,
        Club club,
        Notification notification,
        Storage storage
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

    /** 업로드 파일 저장소. type 은 local | gcs. */
    public record Storage(
            String type,
            Local local,
            Gcs gcs,
            Image image
    ) {
        /** publicBaseUrl 은 origin 만 적는다(예: http://192.168.0.10:8098). 비우면 요청 origin 을 쓴다. */
        public record Local(String dir, String publicBaseUrl) {}

        public record Gcs(String bucket) {}

        public record Image(long maxBytes, int maxPerPost) {}
    }
}
