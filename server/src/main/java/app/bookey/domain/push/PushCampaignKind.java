package app.bookey.domain.push;

/** 캠페인 종류 — 받는 사람과 지켜야 할 규칙이 다르다. */
public enum PushCampaignKind {
    /** 서비스 공지(약관 변경·점검·장애 안내 등) — 로그인할 수 있는 가입자 모두. 광고를 섞으면 안 된다. */
    NOTICE,
    /** 광고성 정보 — 수신 동의한 사람에게만, 08–21시에만, 제목에 '(광고)'·본문에 수신 거부 방법을 붙인다. */
    MARKETING
}
