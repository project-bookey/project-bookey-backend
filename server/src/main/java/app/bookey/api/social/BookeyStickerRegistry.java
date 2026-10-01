package app.bookey.api.social;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BookeyStickerRegistry {

    private static final Pattern CODE = Pattern.compile("^\\[bookey:([a-z0-9-]+):([a-z0-9-]+)]$");

    private static final Map<String, Set<String>> ACTIONS = Map.of(
            "bookmark-ears", Set.of(
                    "wave", "cheer", "cry", "love", "sleep",
                    "lifting", "flattened", "page-turn", "exhausted", "hugging",
                    "front", "side", "back"),
            "dust-reader", Set.of(
                    "read", "panic", "okay", "angry", "sleep",
                    "classic-read", "classic-question", "classic-upset", "classic-rest", "classic-focus"),
            "paper-scrap", Set.of(
                    "wave", "search", "celebrate", "shock", "cry",
                    "classic-search", "classic-down", "classic-panic", "classic-shock", "classic-read"),
            "bookmark-worm", Set.of(
                    "peek", "love", "squashed", "wave", "question",
                    "classic-read", "classic-squashed", "classic-peek", "classic-rest", "classic-question"),
            "pencil-dumpling", Set.of(
                    "oops", "reach", "done", "angry", "sleep",
                    "classic-study", "classic-push", "classic-drop", "classic-write", "classic-rest"),
            "sleepy-sprout", Set.of(
                    "wave", "cheer", "yawn", "cry", "love",
                    "classic-hide", "classic-yawn", "classic-sleep", "classic-peek", "classic-clover")
    );

    private BookeyStickerRegistry() {}

    static boolean isStickerCode(String value) {
        if (value == null) return false;
        Matcher matcher = CODE.matcher(value.trim());
        if (!matcher.matches()) return false;
        return ACTIONS.getOrDefault(matcher.group(1), Set.of()).contains(matcher.group(2));
    }

    static boolean looksLikeStickerCode(String value) {
        return value != null && value.trim().startsWith("[bookey:");
    }
}
