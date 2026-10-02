package app.bookey.api.plaza;

/**
 * 광장 피드 아이템 종류 — 완독 자랑(FINISH).
 * 밑줄(QUOTE)은 걷어냈다. 옛 앱이 여전히 type=QUOTE 로 부르므로 값은 남기고 늘 빈 페이지를 준다.
 */
public enum PlazaItemType {
    QUOTE,
    FINISH
}
