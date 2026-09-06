package app.bookey.domain.quote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteSearchKeywordTest {

    @Test
    @DisplayName("검색어가 없으면 null — 목록은 검색 없이 예전과 같이 동작한다")
    void nullKeywordStaysNull() {
        assertThat(QuoteSearchKeyword.normalize(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열·공백뿐인 검색어는 null 로 본다")
    void blankKeywordBecomesNull() {
        assertThat(QuoteSearchKeyword.normalize("")).isNull();
        assertThat(QuoteSearchKeyword.normalize("   ")).isNull();
        assertThat(QuoteSearchKeyword.normalize("\t\n ")).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백만 떼고 사이 공백은 그대로 둔다")
    void trimsOnlyTheEdges() {
        assertThat(QuoteSearchKeyword.normalize("  바다  ")).isEqualTo("바다");
        assertThat(QuoteSearchKeyword.normalize(" 오래된  미래 ")).isEqualTo("오래된  미래");
    }

    @Test
    @DisplayName("대소문자는 바꾸지 않는다 — 대조는 조회에서 LOWER 로 맞춘다")
    void keepsLetterCase() {
        assertThat(QuoteSearchKeyword.normalize(" Moby Dick ")).isEqualTo("Moby Dick");
    }
}
