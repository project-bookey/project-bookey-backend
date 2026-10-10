package app.bookey.domain.push;

public enum PushCampaignStatus {
    /** 예약 — 시각이 되면 잡이 보내기 시작한다. 이때만 고치거나 취소할 수 있다(보내는 중에도 취소는 된다). */
    SCHEDULED,
    /** 대상자를 펼치고 보내는 중. */
    SENDING,
    /** 모두 보냈다(방해 금지·야간으로 미룬 알림까지). */
    DONE,
    /** 취소 — 아직 안 나간 알림은 지운다. */
    CANCELLED
}
