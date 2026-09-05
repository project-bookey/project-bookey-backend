package app.bookey.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** application.yml 의 bookey.* 설정. */
@ConfigurationProperties(prefix = "bookey")
public record BookeyProperties(
        Jwt jwt,
        Auth auth,
        BookApi bookApi,
        OAuth oauth,
        Club club,
        Notification notification,
        Storage storage
) {

    /** 가입 본인인증. 이메일 인증 코드 정책. */
    public record Auth(EmailCode emailCode) {
        /** expose 가 true 면 코드 발급 응답에 코드를 동봉한다 — 로컬 개발·스모크 전용, 운영은 반드시 false. */
        public record EmailCode(Duration ttl, Duration cooldown, int maxAttempts, boolean expose) {}
    }

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

    public record OAuth(
            List<String> googleClientIds,
            List<String> appleAudiences,
            Long kakaoAppId
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
