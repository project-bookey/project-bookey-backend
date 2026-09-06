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
        Social social,
        Storage storage
) {

    /** 소셜 피드 경제 (§14.2) — 엽서·우표·책갈피·구독 정책. */
    public record Social(
            int postcardDailyFree,
            int postcardMaxLength,
            int postcardCostBookmarks,
            int stampCostBookmarks,
            int subscriptionMonthlyPostcards,
            int subscriptionMonthlyStamps,
            int subscriptionPriceKrw
    ) {}


    /** 가입 본인인증 정책 — 이메일 인증 코드 또는 휴대폰 본인인증(포트원). */
    public record Auth(SignupVerification signupVerification, EmailCode emailCode, Identity identity) {

        /** 가입 시 요구하는 인증 수단. */
        public enum SignupVerification { EMAIL_CODE, IDENTITY }

        /** expose 가 true 면 코드 발급 응답에 코드를 동봉한다 — 로컬 개발·스모크 전용, 운영은 반드시 false. */
        public record EmailCode(Duration ttl, Duration cooldown, int maxAttempts, boolean expose) {}

        /**
         * 포트원 본인인증. apiSecret 이 비어 있고 allowDevStub 이면 "dev-" 접두 id 를 통과시키는
         * 개발 스텁으로 동작한다 — 운영은 application-prod.yml 이 스텁을 끈다.
         */
        public record Identity(String portoneApiSecret, String portoneStoreId,
                               String portoneChannelKey, boolean allowDevStub) {}
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
            String yes24Key,
            Duration cacheTtl,
            /** YES24 큐레이션(베스트셀러 등) 목록 캐시 TTL — 순위는 자주 바뀌므로 검색 캐시보다 짧게. */
            Duration yes24CurationTtl
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
