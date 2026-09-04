package app.bookey.domain.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostExcerptTest {

    @Test
    @DisplayName("이미지는 통째로 지운다")
    void removesImages() {
        assertThat(PostExcerpt.of("![표지](https://img/cover.png) 이 책은 좋았다", 100))
                .isEqualTo("이 책은 좋았다");
    }

    @Test
    @DisplayName("오려둔 문장 표시는 통째로 지운다")
    void removesQuoteMarkers() {
        assertThat(PostExcerpt.of("앞 문장 〖오려둔 문장 123〗 뒤 문장", 100))
                .isEqualTo("앞 문장 뒤 문장");
    }

    @Test
    @DisplayName("표시만 있는 본문은 빈 문자열이 된다")
    void markerOnlyBodyBecomesEmpty() {
        assertThat(PostExcerpt.of("〖오려둔 문장 7〗", 100)).isEmpty();
    }

    @Test
    @DisplayName("링크는 텍스트만 남긴다")
    void keepsLinkText() {
        assertThat(PostExcerpt.of("[원문](https://bookey.app/a)을 읽었다", 100))
                .isEqualTo("원문을 읽었다");
    }

    @Test
    @DisplayName("헤딩·인용·목록 마커를 지운다")
    void removesLineMarkers() {
        String body = """
                # 제목
                > 인용
                - 항목
                1. 첫째
                """;

        assertThat(PostExcerpt.of(body, 100)).isEqualTo("제목 인용 항목 첫째");
    }

    @Test
    @DisplayName("강조 기호를 지운다")
    void removesEmphasis() {
        assertThat(PostExcerpt.of("**굵게** _기울임_ ~~취소선~~", 100))
                .isEqualTo("굵게 기울임 취소선");
    }

    @Test
    @DisplayName("줄바꿈을 사이에 둔 강조도 지운다 — 공백을 먼저 접어야 짝이 이어진다")
    void removesEmphasisAcrossLineBreak() {
        assertThat(PostExcerpt.of("여러 줄\n**굵은\n문장**\n끝", 100))
                .isEqualTo("여러 줄 굵은 문장 끝");
    }

    @Test
    @DisplayName("수평선 줄은 기호를 흘리지 않고 통째로 지운다")
    void removesThematicBreak() {
        assertThat(PostExcerpt.of("구분선\n***\n뒤", 100)).isEqualTo("구분선 뒤");
        assertThat(PostExcerpt.of("구분선\n---\n뒤", 100)).isEqualTo("구분선 뒤");
        assertThat(PostExcerpt.of("구분선\n___\n뒤", 100)).isEqualTo("구분선 뒤");
    }

    @Test
    @DisplayName("코드펜스 블록은 통째로 지운다")
    void removesCodeFence() {
        String body = """
                앞 문장
                ```java
                System.out.println("hi");
                ```
                뒤 문장
                """;

        assertThat(PostExcerpt.of(body, 100)).isEqualTo("앞 문장 뒤 문장");
    }

    @Test
    @DisplayName("인라인 백틱을 지운다")
    void removesInlineCode() {
        assertThat(PostExcerpt.of("`ProgressCalculator` 를 읽었다", 100))
                .isEqualTo("ProgressCalculator 를 읽었다");
    }

    @Test
    @DisplayName("줄바꿈과 연속 공백을 한 칸으로 접는다")
    void collapsesWhitespace() {
        assertThat(PostExcerpt.of("여러   공백\n\n그리고 줄바꿈  ", 100))
                .isEqualTo("여러 공백 그리고 줄바꿈");
    }

    @Test
    @DisplayName("max 를 넘으면 max 글자에서 자르고 말줄임표를 붙인다")
    void truncatesOverMax() {
        assertThat(PostExcerpt.of("가나다라마", 3)).isEqualTo("가나다…");
        assertThat(PostExcerpt.of("가나다", 3)).isEqualTo("가나다");
    }

    @Test
    @DisplayName("자르는 자리에 이모지가 걸리면 글자를 쪼개지 않는다")
    void doesNotSplitSurrogatePair() {
        // "🙂" 는 char 두 개짜리라, 4에서 그냥 자르면 반쪽만 남는다.
        assertThat(PostExcerpt.of("가나다🙂라마", 4)).isEqualTo("가나다…");
        assertThat(PostExcerpt.of("가나다🙂라마", 5)).isEqualTo("가나다🙂…");
    }

    @Test
    @DisplayName("짝이 없는 밑줄은 지우지 않는다 — snake_case 가 뭉개지지 않는다")
    void keepsLoneUnderscore() {
        assertThat(PostExcerpt.of("snake_case 로 적었다", 100))
                .isEqualTo("snake_case 로 적었다");
        assertThat(PostExcerpt.of("on_message_received 를 고쳤다", 100))
                .isEqualTo("on_message_received 를 고쳤다");
    }

    @Test
    @DisplayName("짝이 없는 별표는 지우지 않는다 — 2*3 이 살아남는다")
    void keepsLoneAsterisk() {
        assertThat(PostExcerpt.of("2*3 은 6이다", 100)).isEqualTo("2*3 은 6이다");
        assertThat(PostExcerpt.of("가격은 3~5만원", 100)).isEqualTo("가격은 3~5만원");
    }

    @Test
    @DisplayName("닫히지 않은 코드펜스는 시작부터 끝까지 지운다")
    void removesUnclosedCodeFence() {
        String body = """
                앞 문장
                ```java
                System.out.println("hi");
                남은 코드""";

        assertThat(PostExcerpt.of(body, 100)).isEqualTo("앞 문장");
    }

    @Test
    @DisplayName("max 가 0 이하면 빈 문자열이다")
    void emptyForNonPositiveMax() {
        assertThat(PostExcerpt.of("가나다라마", 0)).isEmpty();
        assertThat(PostExcerpt.of("가나다라마", -1)).isEmpty();
    }

    @Test
    @DisplayName("빈 본문이나 null 은 빈 문자열이다")
    void emptyForBlankBody() {
        assertThat(PostExcerpt.of(null, 100)).isEmpty();
        assertThat(PostExcerpt.of("", 100)).isEmpty();
        assertThat(PostExcerpt.of("   \n  ", 100)).isEmpty();
    }
}
