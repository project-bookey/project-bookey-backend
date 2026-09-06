package app.bookey.api.auth.verifier;

import app.bookey.api.auth.IdentityVerifier;
import app.bookey.api.auth.VerifiedIdentity;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Map;

/**
 * 포트원(PortOne) V2 본인인증 검증.
 * 앱이 포트원 SDK 로 인증을 마치면 identityVerificationId 를 보내고,
 * 서버가 포트원 API 로 결과를 다시 조회해 위조를 막는다 — 클라이언트가 준 값은 id 뿐이다.
 *
 * API Secret 이 비어 있고 allow-dev-stub 이면 "dev-" 접두 id 를 통과시키는 개발 스텁으로 동작한다
 * (같은 id 는 같은 CI 를 돌려줘 중복 가입 차단도 로컬에서 시험할 수 있다). 운영은 스텁이 꺼져 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneIdentityVerifier implements IdentityVerifier {

    private static final String VERIFICATION_URL = "https://api.portone.io/identity-verifications/";

    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    @Override
    @SuppressWarnings("unchecked")
    public VerifiedIdentity verify(String identityVerificationId) {
        BookeyProperties.Auth.Identity config = properties.auth().identity();
        boolean configured = config.portoneApiSecret() != null && !config.portoneApiSecret().isBlank();

        if (!configured) {
            if (config.allowDevStub() && identityVerificationId.startsWith("dev-")) {
                log.warn("본인인증 개발 스텁 통과: id={}", identityVerificationId);
                return new VerifiedIdentity("개발테스터", "01000000000", LocalDate.of(1995, 1, 1),
                        "dev-ci-" + identityVerificationId, "dev-di-" + identityVerificationId);
            }
            throw ApiException.of(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        try {
            Map<String, Object> body = bookApiRestClient.get()
                    .uri(VERIFICATION_URL + identityVerificationId)
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + config.portoneApiSecret())
                    .retrieve()
                    .body(Map.class);
            if (body == null || !"VERIFIED".equals(body.get("status"))) {
                throw ApiException.of(ErrorCode.IDENTITY_VERIFICATION_FAILED);
            }
            Map<String, Object> customer = (Map<String, Object>) body.get("verifiedCustomer");
            if (customer == null || customer.get("ci") == null) {
                throw ApiException.of(ErrorCode.IDENTITY_VERIFICATION_FAILED);
            }
            String birth = (String) customer.get("birthDate");
            return new VerifiedIdentity(
                    (String) customer.get("name"),
                    (String) customer.get("phoneNumber"),
                    birth == null ? null : LocalDate.parse(birth),
                    (String) customer.get("ci"),
                    (String) customer.get("di"));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("포트원 본인인증 조회 실패: id={}", identityVerificationId, e);
            throw ApiException.of(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
    }
}
