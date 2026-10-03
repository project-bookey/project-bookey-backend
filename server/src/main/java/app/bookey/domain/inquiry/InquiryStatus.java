package app.bookey.domain.inquiry;

/** 고객문의 상태 — 문의 하나에 답변 하나라 대기와 완료 둘뿐이다. */
public enum InquiryStatus {
    WAITING,
    ANSWERED
}
