package app.bookey.common.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 엽서 16글자 제한의 기준 — 한글 완성형 글자(grapheme) 세기 (§14.9 확정). */
class GraphemeCounterTest {

    @Test
    @DisplayName("한글 완성형 16자는 16으로 센다 — 자모 조합(NFD)이어도 보이는 글자 수 그대로")
    void koreanSyllables() {
        assertThat(GraphemeCounter.count("가나다라마바사아자차카타파하기니")).isEqualTo(16);
        // NFD 로 풀린 "가" (ㄱ+ㅏ) 도 보이는 글자 1개
        assertThat(GraphemeCounter.count("가")).isEqualTo(1);
    }

    @Test
    @DisplayName("ZWJ 조합 이모지·결합 문자도 보이는 글자 1개로 센다")
    void graphemeClusters() {
        assertThat(GraphemeCounter.count("👨‍👩‍👧")).isEqualTo(1);     // ZWJ 가족 이모지
        assertThat(GraphemeCounter.count("👍🏽")).isEqualTo(1);        // 피부톤 수식
        assertThat(GraphemeCounter.count("e\u0301")).isEqualTo(1);  // e + 결합 악센트
        assertThat(GraphemeCounter.count("\uAC00")).isEqualTo(1);   // 완성형 "가"
        assertThat(GraphemeCounter.count("\u1100\u1161")).isEqualTo(1); // 자모 조합 "가"
    }

    @Test
    @DisplayName("혼합 문자열 — 공백도 한 글자, null·빈 문자열은 0")
    void mixedAndEdge() {
        assertThat(GraphemeCounter.count("책 좋아요 📚")).isEqualTo(7);
        assertThat(GraphemeCounter.count("")).isZero();
        assertThat(GraphemeCounter.count(null)).isZero();
    }
}
