package app.bookey.common.support;

import java.util.regex.Pattern;

/**
 * 보이는 글자(grapheme cluster) 세기 — 엽서 16글자 제한(§14.9 확정: 한글 완성형 글자 기준).
 * "가나다라마바사아자차카타파하기니" = 16자. ZWJ 조합 이모지(👨‍👩‍👧)도 보이는 글자 1개로 센다.
 */
public final class GraphemeCounter {

    /** \X = 확장 grapheme cluster (Java 9+). */
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private GraphemeCounter() {}

    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) GRAPHEME.matcher(text).results().count();
    }
}
