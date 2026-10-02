package app.bookey.domain.post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 옛 글 독후감에 엮인 밑줄을 본문 글로 옮기는 순수 규칙 — 밑줄 테이블을 지우는 V36 마이그레이션이 쓴다.
 *
 * <p>옛 글은 본문에 {@code 〖오려둔 문장 123〗} 표시를 두고 밑줄을 post_quotes 로 엮었다. 앱은 표시 자리에 밑줄 조각을,
 * 표시 없이 엮여만 있던 밑줄은 글 끝에 차례로 그렸다. 밑줄을 지우기 전에 그 모습을 본문 글로 굳힌다 —
 * 앱의 문장 조각 꼴({@code > 문장} 줄들 + 끝줄 {@code > — 출처})이라 앱은 지금처럼 점선 메모 조각으로 그린다.
 * 출처는 책 제목 · 쪽수다. 남의 밑줄이어도 그 사람 닉네임은 넣지 않는다 — 밑줄을 지우는데 남의 이름이 남의 글 본문에
 * 굳어 버리면 그 사람이 탈퇴해도 지워지지 않는다. 문장은 책에서 옮긴 글이라 책이 출처다.
 * 엮인 밑줄에 없는 표시(지워진 밑줄)는 표시만 지운다 — 예전에도 그 자리는 비어 보였다.
 *
 * <p>앱 {@code src/components/post/postQuotes.ts} 의 {@code inlineLegacyQuotes} 와 같은 결과를 낸다(빈 줄 정리까지).
 * 그래서 공백 판정도 자바 {@code strip()} 이 아니라 자바스크립트 {@code trim()} 과 같은 문자 집합을 쓴다.
 */
public final class LegacyQuoteInliner {

    /** 옛 표시 — 밑줄 id 를 담는다. */
    private static final Pattern MARKER = Pattern.compile("〖오려둔 문장 (\\d+)〗");

    /** 글에 엮여 있던 밑줄 한 건 — 글에 붙인 순서대로 넘긴다. */
    public record Quote(long id, String bookTitle, Integer page, String content) {}

    private LegacyQuoteInliner() {
    }

    /** 본문에 옛 표시가 하나라도 있는가. */
    public static boolean hasMarker(String md) {
        return md != null && MARKER.matcher(md).find();
    }

    /**
     * 표시 자리에는 그 밑줄을, 표시 없이 엮여만 있던 밑줄은 글 끝에 차례로 조각 글로 넣는다.
     * 조각은 제 문단이 되게 앞뒤를 빈 줄 하나로 떼고, 표시만 있던 줄을 지운 자리에는 빈 줄이 겹쳐 남지 않게 한다.
     */
    public static String inline(String md, List<Quote> quotes) {
        if (md == null) {
            return null;
        }
        if (quotes.isEmpty() && !hasMarker(md)) {
            return md;
        }
        Map<Long, Quote> byId = new HashMap<>();
        for (Quote quote : quotes) {
            byId.putIfAbsent(quote.id(), quote);
        }
        Builder out = new Builder();
        for (String line : md.split("\n", -1)) {
            Matcher matcher = MARKER.matcher(line);
            if (!matcher.find()) {
                out.push(line);
                continue;
            }
            int last = 0;
            boolean wrote = false;
            do {
                String head = jsTrim(line.substring(last, matcher.start()));
                if (!head.isEmpty()) {
                    out.push(head);
                    wrote = true;
                }
                Quote quote = byId.get(parseId(matcher.group(1)));
                if (quote != null) {
                    out.pushQuote(quote);
                    wrote = true;
                }
                last = matcher.end();
            } while (matcher.find());
            String tail = jsTrim(line.substring(last));
            if (!tail.isEmpty()) {
                out.push(tail);
                wrote = true;
            }
            if (!wrote) {
                out.dropBlank = out.lines.isEmpty() || isJsBlank(out.lastLine());
            }
        }
        for (Quote quote : quotes) {
            if (!out.placed.contains(quote.id())) {
                out.pushQuote(quote);
            }
        }
        return String.join("\n", out.lines);
    }

    /** 조각 하나를 본문 글로 — 줄마다 {@code > }를 달고, 출처가 있으면 끝줄에 {@code > — 출처}. 문장 사이 빈 줄은 {@code >}. */
    static String quoteBlock(String text, String source) {
        String normalized = jsTrim(text.replace("\r\n", "\n").replace('\r', '\n'));
        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            String trimmed = jsTrim(line);
            lines.add(trimmed.isEmpty() ? ">" : "> " + trimmed);
        }
        String tail = source == null ? "" : jsTrim(source);
        if (!tail.isEmpty()) {
            lines.add("> — " + tail);
        }
        return String.join("\n", lines);
    }

    /** 옛 밑줄 한 건 → 조각 글. 출처는 책 제목 · 쪽수(있는 것만). */
    static String legacyBlock(Quote quote) {
        String source = Stream.of(
                        quote.bookTitle() == null || quote.bookTitle().isEmpty() ? null : quote.bookTitle(),
                        quote.page() == null ? null : quote.page() + "쪽")
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "));
        return quoteBlock(quote.content(), source);
    }

    /**
     * 자바스크립트 {@code String.prototype.trim} 과 같은 문자를 양끝에서 벗긴다 — 앱과 같은 결과를 내려고.
     * 자바 {@code strip()} 은 줄 바꿈 없는 공백(U+00A0 등)·BOM 을 남기고 U+001C~1F 를 벗겨 결과가 갈린다.
     */
    static String jsTrim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isJsWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && isJsWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    static boolean isJsBlank(String text) {
        return jsTrim(text).isEmpty();
    }

    /** ECMAScript 의 WhiteSpace + LineTerminator — 탭·세로탭·폼피드·공백류(Zs)·BOM·줄 끝 문자. */
    private static boolean isJsWhitespace(char c) {
        return c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r'
                || c == '\uFEFF' || c == '\u2028' || c == '\u2029'
                || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    private static long parseId(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** 줄을 쌓으며 조각 앞뒤 빈 줄과 지운 표시 자리의 빈 줄을 정리한다. */
    private static final class Builder {
        private final List<String> lines = new ArrayList<>();
        private final Set<Long> placed = new HashSet<>();
        /** 조각 바로 다음 줄이 글이면 빈 줄 하나로 뗀다. */
        private boolean gap;
        /** 표시만 있던 줄을 지웠다면 뒤따르는 빈 줄 하나를 함께 걷는다. */
        private boolean dropBlank;

        private String lastLine() {
            return lines.get(lines.size() - 1);
        }

        private void push(String line) {
            boolean blank = isJsBlank(line);
            if (dropBlank && blank) {
                dropBlank = false;
                return;
            }
            dropBlank = false;
            if (gap && !blank) {
                lines.add("");
            }
            gap = false;
            lines.add(line);
        }

        private void pushQuote(Quote quote) {
            placed.add(quote.id());
            if (!lines.isEmpty() && !isJsBlank(lastLine())) {
                lines.add("");
            }
            lines.addAll(List.of(legacyBlock(quote).split("\n", -1)));
            gap = true;
            dropBlank = false;
        }
    }
}
