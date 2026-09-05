package app.bookey.domain.quote;

/**
 * 오려둔 문장 검색어 다듬기 — 목록 API 세 곳(내 밑줄 · 책별 밑줄 · 광장 피드)이 같은 규칙을 쓴다.
 *
 * <p>앞뒤 공백만 떼고 그대로 쓴다. 사이 공백을 줄이지 않는 까닭은 대조 대상이 DB 에 저장된 원문이라
 * 검색어만 다듬으면 오히려 어긋나기 때문이다.
 */
public final class QuoteSearchKeyword {

    private QuoteSearchKeyword() {
    }

    /**
     * 검색에 쓸 말을 돌려준다. 없거나 공백뿐이면 {@code null} — 호출자는 이때 검색 없는 기존 목록을 그대로 쓴다.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
