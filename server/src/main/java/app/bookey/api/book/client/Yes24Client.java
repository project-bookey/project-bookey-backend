package app.bookey.api.book.client;

import app.bookey.common.config.BookeyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 예스24 오픈API (§F1 검색 폴백 · §14.2 애드온 링크).
 * 공통 봉투 {success, data:{meta, items[]}}, 인증은 X-Api-Key 헤더.
 * 상품 응답에는 제휴 애드온 링크(addOnLink)와 목차·소개(contentDetail)가 함께 온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Yes24Client {

    private static final String BASE_URL = "https://apis.yes24.com/v1";
    /** 국내도서 최상위 카테고리 — 큐레이션 기본값. */
    public static final String CATEGORY_DOMESTIC = "001";
    private static final DateTimeFormatter PUBLISH_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    /** 큐레이션 종류 — path 가 곧 예스24 엔드포인트다. */
    public enum CurationKind {
        BESTSELLER("bestseller"),
        STEADY("bestsellerSteady"),
        NEW("newproduct");

        final String path;

        CurationKind(String path) {
            this.path = path;
        }
    }

    /** 정규화한 도서 + YES24 부가 정보. */
    public record Yes24Item(ExternalBook book, String purchaseLink, String addonLink,
                            String tableOfContents, String introduction) {}

    public boolean isConfigured() {
        String key = properties.bookApi().yes24Key();
        return key != null && !key.isBlank();
    }

    /** 베스트셀러·스테디셀러·신상품 — categoryId 필수(기본 국내도서). */
    public List<Yes24Item> curation(CurationKind kind, int size) {
        return fetchItems(UriComponentsBuilder.fromUriString(BASE_URL + "/category/" + kind.path)
                .queryParam("categoryId", CATEGORY_DOMESTIC)
                .queryParam("page", 1)
                .queryParam("pageSize", Math.min(size, 50)));
    }

    public List<ExternalBook> search(String keyword, int size) {
        return fetchItems(UriComponentsBuilder.fromUriString(BASE_URL + "/goods/itemList")
                .queryParam("query", keyword)
                .queryParam("page", 1)
                .queryParam("pageSize", Math.min(size, 50)))
                .stream().map(Yes24Item::book).toList();
    }

    /** 상품 상세 — query 는 ISBN13 이다. */
    public Optional<Yes24Item> detailByIsbn13(String isbn13) {
        return fetchItems(UriComponentsBuilder.fromUriString(BASE_URL + "/goods/itemDetail")
                .queryParam("query", isbn13))
                .stream().findFirst();
    }

    private List<Yes24Item> fetchItems(UriComponentsBuilder builder) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            URI uri = builder.build().encode().toUri();
            JsonNode response = bookApiRestClient.get()
                    .uri(uri)
                    .header("X-Api-Key", properties.bookApi().yes24Key())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("success").asBoolean(false)) {
                log.warn("YES24 API 실패: {}", response == null ? "빈 응답" : response.path("message").asText());
                return List.of();
            }
            List<Yes24Item> items = new ArrayList<>();
            for (JsonNode node : response.path("data").path("items")) {
                Yes24Item item = toItem(node);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        } catch (Exception e) {
            log.warn("YES24 API 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /** 도서만 정규화한다 — isbn13 없는 상품(굿즈 등)은 건너뛴다. */
    private static Yes24Item toItem(JsonNode node) {
        String isbn13 = node.path("isbn13").asText("");
        if (isbn13.isBlank()) {
            return null;
        }
        String rawAuthor = node.path("author").asText("");
        JsonNode content = node.path("contentDetail");
        ExternalBook book = new ExternalBook(
                isbn13,
                node.path("title").asText(null),
                null,
                parseAuthor(rawAuthor),
                parseTranslator(rawAuthor),
                node.path("publisher").asText(null),
                parsePublishDate(node.path("publishDate").asText("")),
                null,  // 페이지 수는 제공하지 않는다 — 알라딘 보강이 계속 담당
                node.path("cover").asText(null),
                parseCategory(node.path("goodsSortNm").asText("")),
                null);
        return new Yes24Item(book,
                node.path("link").asText(null),
                node.path("addOnLink").asText(null),
                content.path("tableOfContents").asText(null),
                content.path("bookIntroduction").asText(null));
    }

    /** "로버트 C. 마틴 저/박재호,이해영 공역" → 저자 부분만. 구분자가 없으면 원문 그대로. */
    static String parseAuthor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (String part : raw.split("/")) {
            String trimmed = part.trim();
            if (trimmed.endsWith(" 저") || trimmed.endsWith("저")) {
                return trimmed.replaceAll("\\s*저$", "").trim();
            }
        }
        return raw.split("/")[0].trim();
    }

    /** "…저/박재호,이해영 공역" → 역자 부분. 없으면 null. */
    static String parseTranslator(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (String part : raw.split("/")) {
            String trimmed = part.trim();
            if (trimmed.endsWith("역")) {
                return trimmed.replaceAll("\\s*(공역|편역|역)$", "").trim();
            }
        }
        return null;
    }

    /** "yyyyMMdd" → LocalDate. 형식이 어긋나면 null. */
    static LocalDate parsePublishDate(String raw) {
        if (raw == null || raw.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(raw, PUBLISH_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /** "국내도서-IT 모바일" → "국내도서 > IT 모바일" (알라딘 카테고리 표기와 톤을 맞춘다). */
    static String parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.replace("-", " > ");
    }
}
