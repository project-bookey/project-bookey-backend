package app.bookey.domain.post;

import java.util.List;
import java.util.regex.Pattern;

/** 독후감 본문(마크다운)을 목록 카드에 쓸 한 줄 발췌로 만드는 순수 규칙. */
public final class PostExcerpt {

    /** 세 개의 백틱으로 감싼 코드 블록 — 통째로 버린다. */
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```.*?```");
    /** 닫히지 않은 코드펜스 — 여는 백틱부터 본문 끝까지 버린다(코드가 발췌로 새지 않게). */
    private static final Pattern UNCLOSED_CODE_FENCE = Pattern.compile("(?s)```.*");
    /** 이미지 `![대체](주소)` — 대체 텍스트까지 버린다. */
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    /** 링크 `[텍스트](주소)` — 텍스트만 남긴다. */
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    /** 줄머리의 인용 `>`·헤딩 `#`·목록 마커(`-`,`*`,`+`,`1.`). */
    private static final Pattern LINE_MARKER =
            Pattern.compile("(?m)^[ \\t]*(?:[>#]+[ \\t]*)*(?:[-*+][ \\t]+|\\d+\\.[ \\t]+)?");
    /** 낱말을 이루는 글자 — 밑줄 강조가 낱말 안에서는 열리지 않게 판별하는 데 쓴다. */
    private static final String WORD = "\\p{IsAlphabetic}\\p{IsDigit}_";
    /**
     * 강조·인라인 코드는 여는 기호와 닫는 기호가 짝을 이룰 때만 벗긴다 — 안쪽 텍스트는 남는다.
     * 짝이 없이 홀로 선 `*`·`_`·`~` 는 그대로 둔다(`snake_case`·`2*3` 가 뭉개지지 않게).
     * 긴 기호(`**`,`__`,`~~`)를 먼저 벗겨야 짧은 규칙이 반쪽만 먹지 않는다.
     * 밑줄은 낱말 안(`on_message_received`)에서는 강조가 아니므로 양옆이 낱말 글자가 아닐 때만 벗긴다.
     */
    private static final List<Pattern> EMPHASIS_PAIRS = List.of(
            Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*"),
            Pattern.compile("(?<![" + WORD + "])__(?=\\S)(.+?)(?<=\\S)__(?![" + WORD + "])"),
            Pattern.compile("~~(?=\\S)(.+?)(?<=\\S)~~"),
            Pattern.compile("\\*(?=\\S)(.+?)(?<=\\S)\\*"),
            Pattern.compile("(?<![" + WORD + "])_(?=\\S)(.+?)(?<=\\S)_(?![" + WORD + "])"),
            Pattern.compile("`(?=\\S)(.+?)(?<=\\S)`"));
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PostExcerpt() {
    }

    /** 마크다운 본문을 공백 한 칸으로 접은 한 줄로 만들고, max 를 넘으면 잘라 말줄임표를 붙인다. */
    public static String of(String bodyMd, int max) {
        if (bodyMd == null || bodyMd.isBlank() || max <= 0) {
            return "";
        }
        String text = CODE_FENCE.matcher(bodyMd).replaceAll(" ");
        text = UNCLOSED_CODE_FENCE.matcher(text).replaceAll(" ");
        text = IMAGE.matcher(text).replaceAll(" ");
        text = LINK.matcher(text).replaceAll("$1");
        text = LINE_MARKER.matcher(text).replaceAll("");
        for (Pattern pair : EMPHASIS_PAIRS) {
            text = pair.matcher(text).replaceAll("$1");
        }
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.length() <= max) {
            return text;
        }
        // 이모지처럼 char 두 개로 된 글자가 자르는 자리에 걸리면 반쪽만 남지 않도록 한 칸 앞에서 자른다.
        int cut = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
        return text.substring(0, cut) + "…";
    }
}
