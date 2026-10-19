package app.bookey.api.novel;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;

/**
 * 릴레이노벨 앱의 스토어 결제를 확인할 자격 증명. 북키 앱과 같은 개발자 계정의 키(App Store Server API 키, Play 서비스 계정)를 쓰고
 * 앱 id(번들·패키지)만 릴레이노벨 것으로 바꾼다. 키가 없으면 결제를 확인할 수 없으니 거절한다.
 */
final class NovelStoreCredentials {
    private NovelStoreCredentials() {}

    static BookeyProperties.Payment.Apple apple(BookeyProperties.Payment payment, String bundleId) {
        if (payment == null || payment.apple() == null || blank(bundleId)) throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        BookeyProperties.Payment.Apple bookey = payment.apple();
        if (blank(bookey.issuerId()) || blank(bookey.keyId()) || blank(bookey.privateKey()))
            throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        return new BookeyProperties.Payment.Apple(bookey.issuerId(), bookey.keyId(), bundleId, bookey.privateKey(),
                bookey.environment());
    }

    /** Play Console 에서 그 서비스 계정에 릴레이노벨 앱 권한을 줘야 한다. */
    static BookeyProperties.Payment.Google google(BookeyProperties.Payment payment, String packageName) {
        if (payment == null || payment.google() == null || blank(packageName)) throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        BookeyProperties.Payment.Google bookey = payment.google();
        if (blank(bookey.serviceAccountEmail()) || blank(bookey.privateKey())) throw ApiException.of(ErrorCode.PAYMENT_NOT_CONFIGURED);
        return new BookeyProperties.Payment.Google(packageName, bookey.serviceAccountEmail(), bookey.privateKey(),
                bookey.rtdnToken());
    }

    static boolean blank(String value) { return value == null || value.isBlank(); }
}
