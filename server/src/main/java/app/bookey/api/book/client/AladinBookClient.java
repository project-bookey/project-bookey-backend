package app.bookey.api.book.client;

import app.bookey.common.config.BookeyProperties;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 알라딘 OpenAPI — 메타 보강 (§F1).
 * ItemLookUp 의 subInfo.itemPage 로 페이지 수를 얻는다. 진척도 계산의 핵심 값.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AladinBookClient {

    private static final String SEARCH_URL = "https://www.aladin.co.kr/ttb/api/ItemSearch.aspx";
    private static final String LOOKUP_URL = "https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx";

    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    public boolean isConfigured() {
        String key = properties.bookApi().aladinTtbKey();
        return key != null && !key.isBlank();
    }

    public Optional<ExternalBook> lookupByIsbn13(String isbn13) {
        if (!isConfigured() || isbn13 == null || isbn13.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(LOOKUP_URL)
                    .queryParam("ttbkey", properties.bookApi().aladinTtbKey())
                    .queryParam("itemIdType", "ISBN13")
                    .queryParam("ItemId", isbn13)
                    .queryParam("output", "js")
                    .queryParam("Version", "20131101")
                    .queryParam("OptResult", "packing")
                    .build()
                    .encode()
                    .toUri();

            JsonNode response = bookApiRestClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null) {
                return Optional.empty();
            }
            JsonNode item = response.path("item").path(0);
            if (item.isMissingNode() || item.isNull()) {
                return Optional.empty();
            }

            Integer itemPage = item.path("subInfo").path("itemPage").isNumber()
                    ? item.path("subInfo").path("itemPage").asInt()
                    : null;

            return Optional.of(new ExternalBook(
                    isbn13,
                    item.path("title").asText(null),
                    null,
                    item.path("author").asText(null),
                    null,
                    item.path("publisher").asText(null),
                    parseDate(item.path("pubDate").asText(null)),
                    itemPage != null && itemPage > 0 ? itemPage : null,
                    emptyToNull(item.path("cover").asText(null)),
                    emptyToNull(item.path("categoryName").asText(null)),
                    emptyToNull(item.path("description").asText(null))));
        } catch (Exception e) {
            log.warn("Aladin lookup failed for {}: {}", isbn13, e.getMessage());
            return Optional.empty();
        }
    }

    public List<ExternalBook> search(String keyword, int size) {
        if (!isConfigured() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("ttbkey", properties.bookApi().aladinTtbKey())
                    .queryParam("Query", keyword)
                    .queryParam("QueryType", "Keyword")
                    .queryParam("MaxResults", Math.min(size, 50))
                    .queryParam("start", 1)
                    .queryParam("SearchTarget", "Book")
                    .queryParam("output", "js")
                    .queryParam("Version", "20131101")
                    .queryParam("OptResult", "packing")
                    .build()
                    .encode()
                    .toUri();

            JsonNode response = bookApiRestClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null) {
                return List.of();
            }
            List<ExternalBook> results = new ArrayList<>();
            for (JsonNode item : response.path("item")) {
                ExternalBook book = toExternalBook(item);
                if (book.title() != null && !book.title().isBlank()) {
                    results.add(book);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Aladin search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private ExternalBook toExternalBook(JsonNode item) {
        Integer itemPage = item.path("subInfo").path("itemPage").isNumber()
                ? item.path("subInfo").path("itemPage").asInt()
                : null;

        return new ExternalBook(
                emptyToNull(item.path("isbn13").asText(null)),
                item.path("title").asText(null),
                null,
                item.path("author").asText(null),
                null,
                item.path("publisher").asText(null),
                parseDate(item.path("pubDate").asText(null)),
                itemPage != null && itemPage > 0 ? itemPage : null,
                emptyToNull(item.path("cover").asText(null)),
                emptyToNull(item.path("categoryName").asText(null)),
                emptyToNull(item.path("description").asText(null)));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
