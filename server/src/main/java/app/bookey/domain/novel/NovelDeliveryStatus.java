package app.bookey.domain.novel;

/** 배달된 열매 — 들고 있음(DELIVERED) → 이어 씀(WRITTEN) / 넘김(DECLINED) / 시간이 지남(EXPIRED). */
public enum NovelDeliveryStatus { DELIVERED, WRITTEN, DECLINED, EXPIRED }
