package app.bookey.domain.legal;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 법적 문서의 단일 원천 — 앱 가입 화면과 웹 정책 페이지(/legal/index.html)가 모두 여기서 원문을 받는다.
 * 원문은 classpath:legal/{key}.txt 의 줄글이고, 문구를 고치면 version 을 올린다 —
 * 가입·동의 요청은 화면에 보여 준 문서의 version 을 함께 보내고, 서버는 지금 version 과 같을 때만 받는다.
 * published=false 는 아직 화면에 띄우지 않는 초안이라 공개 API 가 404 를 준다.
 */
@Getter
public enum LegalDocument {
    TERMS("terms", "Bookey 이용약관", "2026-10-04", true),
    PRIVACY_CONSENT("privacy-consent", "개인정보 수집·이용 동의", "2026-10-04", true),
    PROFILE_OPTIONAL("profile-optional", "선택 정보(성별·생년월일) 수집·이용 동의", "2026-10-04", true),
    MARKETING("marketing", "광고성 정보 수신 동의", "2026-10-04", true),
    /** YES24 적립 제휴 — 계약서의 제공 항목을 확인하기 전까지 비공개 초안. */
    THIRD_PARTY_YES24("third-party-yes24", "개인정보 제3자 제공 동의 (YES24)", "draft", false),
    PRIVACY_POLICY("privacy-policy", "개인정보처리방침", "2026-10-04", true),
    REFUND_POLICY("refund", "환불 정책", "2026-10-04", true);

    private final String key;
    private final String title;
    private final String version;
    private final boolean published;

    LegalDocument(String key, String title, String version, boolean published) {
        this.key = key;
        this.title = title;
        this.version = version;
        this.published = published;
    }

    /** 시행일 — 버전 문자열이 곧 시행일이다(초안은 없음). */
    public LocalDate effectiveDate() {
        return published ? LocalDate.parse(version) : null;
    }

    public String resourcePath() {
        return "legal/" + key + ".txt";
    }

    public static java.util.Optional<LegalDocument> fromKey(String key) {
        for (LegalDocument document : values()) {
            if (document.key.equals(key)) {
                return java.util.Optional.of(document);
            }
        }
        return java.util.Optional.empty();
    }
}
