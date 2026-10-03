package app.bookey.domain.inquiry;

import lombok.Getter;

/**
 * 고객문의 유형 — FAQ 분류에도 같은 값을 쓴다.
 * 선언 순서가 곧 앱 칩 순서이고, 맨 앞 값이 작성 화면의 기본값이다(가장 흔한 유형을 앞에 둔다).
 */
@Getter
public enum InquiryCategory {
    USAGE("이용 문의"),
    ACCOUNT("계정·로그인"),
    BUG("오류 신고"),
    PAYMENT("결제·구독"),
    SUGGESTION("제안"),
    ETC("기타");

    private final String label;

    InquiryCategory(String label) {
        this.label = label;
    }
}
