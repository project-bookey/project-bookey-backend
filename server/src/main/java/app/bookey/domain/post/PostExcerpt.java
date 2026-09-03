package app.bookey.domain.post;

import java.util.regex.Pattern;

/** 독후감 본문(마크다운)을 목록 카드에 쓸 한 줄 발췌로 만드는 순수 규칙. */
public final class PostExcerpt {

    /** 세 개의 백틱으로 감싼 코드 블록 — 통째로 버린다. */
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```.*?```");
    /** 이미지 `![대체](주소)` — 대체 텍스트까지 버린다. */
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    /** 링크 `[텍스트](주소)` — 텍스트만 남긴다. */
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    /** 줄머리의 인용 `>`·헤딩 `#`·목록 마커(`-`,`*`,`+`,`1.`). */
    private static final Pattern LINE_MARKER =
            Pattern.compile("(?m)^[ \\t]*(?:[>#]+[ \\t]*)*(?:[-*+][ \\t]+|\\d+\\.[ \\t]+)?");
    /** 강조·인라인 코드 기호. */
    private static final Pattern EMPHASIS = Pattern.compile("[*_~`]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PostExcerpt() {
    }

    /** 마크다운 본문을 공백 한 칸으로 접은 한 줄로 만들고, max 를 넘으면 잘라 말줄임표를 붙인다. */
    public static String of(String bodyMd, int max) {
        if (bodyMd == null || bodyMd.isBlank()) {
            return "";
        }
        String text = CODE_FENCE.matcher(bodyMd).replaceAll(" ");
        text = IMAGE.matcher(text).replaceAll(" ");
        text = LINK.matcher(text).replaceAll("$1");
        text = LINE_MARKER.matcher(text).replaceAll("");
        text = EMPHASIS.matcher(text).replaceAll("");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.length() <= max) {
            return text;
        }
        // 이모지처럼 char 두 개로 된 글자가 자르는 자리에 걸리면 반쪽만 남지 않도록 한 칸 앞에서 자른다.
        int cut = max > 0 && Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
        return text.substring(0, cut) + "…";
    }
}
