package app.bookey.domain.post;

import app.bookey.domain.post.LegacyQuoteInliner.Quote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 앱 postQuotes.ts 의 inlineLegacyQuotes 와 같은 기대값 — 두 쪽이 같은 글을 만들어야 지운 뒤에도 글이 그대로 보인다. */
class LegacyQuoteInlinerTest {

    private static Quote quote(long id, String content) {
        return new Quote(id, "책", null, content);
    }

    private static Quote quote(long id, String content, Integer page) {
        return new Quote(id, "책", page, content);
    }

    // ────────────────────────────── quoteBlock ──────────────────────────────

    @Test
    @DisplayName("quoteBlock: 줄마다 > 를 달고, 출처가 있으면 끝줄에 > — 출처")
    void quoteBlockFormatsLinesAndSource() {
        assertThat(LegacyQuoteInliner.quoteBlock("문장 하나", null)).isEqualTo("> 문장 하나");
        assertThat(LegacyQuoteInliner.quoteBlock("문장 하나", "12쪽")).isEqualTo("> 문장 하나\n> — 12쪽");
    }

    @Test
    @DisplayName("quoteBlock: 줄 앞뒤 빈칸을 버리고 문장 사이 빈 줄은 > 한 줄로 남긴다(CRLF 도)")
    void quoteBlockTrimsAndKeepsBlankLines() {
        assertThat(LegacyQuoteInliner.quoteBlock("  첫 줄  \r\n\r\n  둘째 줄 ", " ")).isEqualTo("> 첫 줄\n>\n> 둘째 줄");
    }

    @Test
    @DisplayName("quoteBlock: 자바스크립트 trim 처럼 줄 바꿈 없는 공백·BOM·전각 공백도 벗긴다")
    void quoteBlockTrimsLikeJavaScript() {
        assertThat(LegacyQuoteInliner.quoteBlock("\u00A0문장\u00A0", null)).isEqualTo("> 문장");
        assertThat(LegacyQuoteInliner.quoteBlock("\uFEFF\u3000문장\u202F", null)).isEqualTo("> 문장");
        // 자바스크립트 trim 은 U+001C~1F 를 남긴다 — 자바 strip() 과 다른 지점.
        assertThat(LegacyQuoteInliner.jsTrim("\u001C문장")).isEqualTo("\u001C문장");
    }

    // ────────────────────────────── legacyBlock ──────────────────────────────

    @Test
    @DisplayName("legacyBlock: 출처는 책 제목 · 쪽수 — 남의 밑줄이어도 닉네임은 넣지 않는다")
    void legacyBlockSource() {
        assertThat(LegacyQuoteInliner.legacyBlock(quote(1L, "문장", 12))).isEqualTo("> 문장\n> — 책 · 12쪽");
        assertThat(LegacyQuoteInliner.legacyBlock(quote(1L, "문장"))).isEqualTo("> 문장\n> — 책");
        assertThat(LegacyQuoteInliner.legacyBlock(new Quote(1L, "", null, "문장"))).isEqualTo("> 문장");
    }

    // ────────────────────────────── inline ──────────────────────────────

    @Test
    @DisplayName("밑줄도 표시도 없는 글은 그대로 둔다")
    void untouchedWithoutQuotes() {
        assertThat(LegacyQuoteInliner.inline("그냥 글", List.of())).isEqualTo("그냥 글");
        assertThat(LegacyQuoteInliner.inline("  〖괄호〗 그대로  ", List.of())).isEqualTo("  〖괄호〗 그대로  ");
    }

    @Test
    @DisplayName("제 문단에 홀로 있던 표시는 그 자리에 조각 글이 된다")
    void markerOnOwnParagraph() {
        assertThat(LegacyQuoteInliner.inline("앞\n\n〖오려둔 문장 1〗\n\n뒤", List.of(quote(1L, "문장", 12))))
                .isEqualTo("앞\n\n> 문장\n> — 책 · 12쪽\n\n뒤");
    }

    @Test
    @DisplayName("엮이지 않은(지워진) 밑줄 표시는 지우고, 남는 빈 줄은 하나로 접는다")
    void unknownMarkerIsRemoved() {
        assertThat(LegacyQuoteInliner.inline("앞\n\n〖오려둔 문장 5〗\n\n뒤", List.of())).isEqualTo("앞\n\n뒤");
        assertThat(LegacyQuoteInliner.inline("〖오려둔 문장 5〗\n\n뒤", List.of())).isEqualTo("뒤");
        // 줄 바꿈 없는 공백만 남은 줄도 빈 줄이다(자바스크립트 trim 과 같게).
        assertThat(LegacyQuoteInliner.inline("앞\n\n〖오려둔 문장 5〗\n\u00A0\n뒤", List.of())).isEqualTo("앞\n\n뒤");
    }

    @Test
    @DisplayName("글 사이에 낀 표시는 앞뒤 글과 빈 줄로 떼어 제 문단이 된다")
    void inlineMarkerSplitsParagraph() {
        assertThat(LegacyQuoteInliner.inline("앞 〖오려둔 문장 1〗 뒤", List.of(quote(1L, "Q"))))
                .isEqualTo("앞\n\n> Q\n> — 책\n\n뒤");
    }

    @Test
    @DisplayName("맞붙은 표시 둘은 조각 둘이 된다")
    void adjacentMarkers() {
        assertThat(LegacyQuoteInliner.inline("〖오려둔 문장 1〗〖오려둔 문장 2〗", List.of(quote(1L, "A"), quote(2L, "B"))))
                .isEqualTo("> A\n> — 책\n\n> B\n> — 책");
    }

    @Test
    @DisplayName("표시 없이 엮여만 있던 밑줄은 글 끝에 붙인 순서대로 옮긴다")
    void leftoversAreAppended() {
        assertThat(LegacyQuoteInliner.inline("본문\n", List.of(quote(1L, "A"), quote(2L, "B", 3))))
                .isEqualTo("본문\n\n> A\n> — 책\n\n> B\n> — 책 · 3쪽");
        assertThat(LegacyQuoteInliner.inline("〖오려둔 문장 2〗\n\n끝", List.of(quote(1L, "A"), quote(2L, "B"))))
                .isEqualTo("> B\n> — 책\n\n끝\n\n> A\n> — 책");
    }

    @Test
    @DisplayName("같은 표시가 두 번이면 조각도 두 번 — 예전 화면도 그 자리마다 그렸다")
    void duplicateMarkersRenderTwice() {
        assertThat(LegacyQuoteInliner.inline("〖오려둔 문장 1〗\n\n〖오려둔 문장 1〗", List.of(quote(1L, "A"))))
                .isEqualTo("> A\n> — 책\n\n> A\n> — 책");
    }

    @Test
    @DisplayName("표시 바로 다음 줄이 글이면 빈 줄 하나로 뗀다")
    void textRightAfterMarkerGetsGap() {
        assertThat(LegacyQuoteInliner.inline("〖오려둔 문장 1〗\n이어", List.of(quote(1L, "A"))))
                .isEqualTo("> A\n> — 책\n\n이어");
    }

    @Test
    @DisplayName("여러 줄 문장도 앱과 같은 꼴로 옮긴다")
    void multilineQuote() {
        Quote multiline = new Quote(3L, "노르웨이의 숲", 30, "첫 줄\n\n둘째 줄");
        assertThat(LegacyQuoteInliner.inline("앞\n\n〖오려둔 문장 3〗", List.of(multiline)))
                .isEqualTo("앞\n\n> 첫 줄\n>\n> 둘째 줄\n> — 노르웨이의 숲 · 30쪽");
    }

    @Test
    @DisplayName("hasMarker: 옛 표시가 있는지만 본다")
    void hasMarker() {
        assertThat(LegacyQuoteInliner.hasMarker("앞 〖오려둔 문장 12〗")).isTrue();
        assertThat(LegacyQuoteInliner.hasMarker("〖오려둔 문장〗")).isFalse();
        assertThat(LegacyQuoteInliner.hasMarker(null)).isFalse();
    }
}
