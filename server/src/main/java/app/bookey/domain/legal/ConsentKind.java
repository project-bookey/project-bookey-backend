package app.bookey.domain.legal;

import lombok.Getter;

/**
 * 동의 기록의 종류. document 가 있으면 동의할 때 그 문서의 version 을 함께 남긴다.
 * selfService 는 회원이 앱에서 직접 켜고 끌 수 있는 선택 동의다(PUT /api/v1/me/consents/{kind}).
 */
@Getter
public enum ConsentKind {
    /** [필수] 이용약관 */
    TERMS(LegalDocument.TERMS, false),
    /** [필수] 개인정보 수집·이용 */
    PRIVACY(LegalDocument.PRIVACY_CONSENT, false),
    /** [필수] 만 14세 이상 확인 — 문서 없이 본인 확인만 남긴다. */
    AGE_14(null, false),
    /** [선택] 성별·생년월일 수집·이용 — 철회하면 두 값을 지운다. */
    PROFILE_OPTIONAL(LegalDocument.PROFILE_OPTIONAL, true),
    /** [선택] 광고성 정보 수신(앱 푸시·이메일) — 동의·철회 때마다 처리 결과를 인앱 알림으로 알린다. */
    MARKETING(LegalDocument.MARKETING, true),
    /** [선택] YES24 제3자 제공 — 제휴 기능을 열 때 그 화면에서 받는다(지금은 받지 않음). */
    THIRD_PARTY_YES24(LegalDocument.THIRD_PARTY_YES24, false);

    private final LegalDocument document;
    private final boolean selfService;

    ConsentKind(LegalDocument document, boolean selfService) {
        this.document = document;
        this.selfService = selfService;
    }

    /** 지금 동의하면 남길 문서 버전 — 문서가 없는 종류는 null. */
    public String currentVersion() {
        return document == null ? null : document.getVersion();
    }
}
