package app.bookey.api.auth;

/** 휴대폰 본인인증 검증 채널. 실패는 ApiException(IDENTITY_VERIFICATION_FAILED). */
public interface IdentityVerifier {

    VerifiedIdentity verify(String identityVerificationId);
}
