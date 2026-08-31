package app.bookey.domain.book;

import lombok.Getter;

/**
 * 최소 독서시간 산정 계수 (§F6).
 * 최소 요구 시간(분) = 총 페이지 × 0.7 × 0.35 × coefficient
 */
@Getter
public enum GenreKey {
    GENERAL(1.0),
    NOVEL(1.0),
    ESSAY(0.9),
    HUMANITIES(1.2),
    TECH(1.2),
    SCIENCE(1.2),
    SELF_HELP(0.9),
    COMIC(0.2),
    PICTURE_BOOK(0.2),
    POETRY(0.7);

    private final double coefficient;

    GenreKey(double coefficient) {
        this.coefficient = coefficient;
    }

    /** 외부 API 카테고리 문자열에서 장르를 추정한다. 실패 시 GENERAL. */
    public static GenreKey fromCategory(String category) {
        if (category == null || category.isBlank()) {
            return GENERAL;
        }
        String c = category.toLowerCase();
        if (c.contains("만화") || c.contains("comic") || c.contains("만화책")) {
            return COMIC;
        }
        if (c.contains("그림책") || c.contains("유아")) {
            return PICTURE_BOOK;
        }
        if (c.contains("시") && c.contains("문학")) {
            return POETRY;
        }
        if (c.contains("소설") || c.contains("fiction")) {
            return NOVEL;
        }
        if (c.contains("에세이") || c.contains("essay")) {
            return ESSAY;
        }
        if (c.contains("인문") || c.contains("역사") || c.contains("철학")) {
            return HUMANITIES;
        }
        if (c.contains("it") || c.contains("컴퓨터") || c.contains("공학") || c.contains("기술")) {
            return TECH;
        }
        if (c.contains("과학") || c.contains("science")) {
            return SCIENCE;
        }
        if (c.contains("자기계발") || c.contains("자기개발")) {
            return SELF_HELP;
        }
        return GENERAL;
    }
}
