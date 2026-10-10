package app.bookey.domain.admin;

/**
 * 관리자 웹이 메뉴·버튼을 가릴 때 쓰는 권한 목록. 역할별 판정은 {@link AdminRole} 이 한다 —
 * 웹이 역할표를 따로 들고 있지 않도록 내 정보(AdminProfile)에 이 목록을 실어 보낸다.
 */
public enum AdminCapability {
    /** 신고 처리·검증 등급 조정·모임 조치 */
    MODERATE,
    /** 경고를 넘는 제재와 해제, 지갑·구독 조정 */
    SANCTION,
    /** 경고 */
    WARN,
    /** 고객문의 답변, FAQ 편집 */
    HANDLE_SUPPORT,
    /** 도서 메타 수정 */
    EDIT_BOOK,
    /** 도서 병합 — 되돌릴 수 없다 */
    MERGE_BOOKS,
    /** 배너·공지·에디터 픽 */
    MANAGE_CONTENT,
    /** 운영 스위치 */
    MANAGE_OPS,
    /** 관리자 계정 관리 */
    MANAGE_ADMINS,
    /** 전체 푸시 발송 */
    BROADCAST,
    /** 결제·지갑 내역 열람 */
    VIEW_PAYMENTS,
    /** 회원 이메일 전체 보기 */
    VIEW_PII
}
