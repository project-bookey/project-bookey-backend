package app.bookey.api.book.client;

import app.bookey.common.config.BookeyProperties;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Google Books — 해외서 폴백 (§F1). pageCount 제공. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleBooksClient {

    private static final String SEARCH_URL = "https://www.googleapis.com/books/v1/volumes";

    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    public List<ExternalBook> search(String keyword, int size) {
        try {
            var builder = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("q", keyword)
                    .queryParam("maxResults", Math.min(size, 40));
            String key = properties.bookApi().googleBooksKey();
            if (key != null && !key.isBlank()) {
                builder.queryParam("key", key);
            }

            JsonNode response = bookApiRestClient.get()
                    .uri(builder.build().encode().toUriString())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return List.of();
            }
            List<ExternalBook> results = new ArrayList<>();
            for (JsonNode item : response.path("items")) {
                results.add(toExternalBook(item.path("volumeInfo")));
            }
            return results;
        } catch (Exception e) {
            log.warn("Google Books search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private ExternalBook toExternalBook(JsonNode info) {
        String isbn13 = null;
        for (JsonNode id : info.path("industryIdentifiers")) {
            if ("ISBN_13".equals(id.path("type").asText())) {
                isbn13 = id.path("identifier").asText(null);
                break;
            }
        }
        List<String> authors = new ArrayList<>();
        info.path("authors").forEach(a -> authors.add(a.asText()));
        List<String> categories = new ArrayList<>();
        info.path("categories").forEach(c -> categories.add(c.asText()));

        Integer pageCount = info.path("pageCount").isNumber() ? info.path("pageCount").asInt() : null;

        return new ExternalBook(
                isbn13,
                info.path("title").asText(null),
                emptyToNull(info.path("subtitle").asText(null)),
                authors.isEmpty() ? null : String.join(", ", authors),
                null,
                emptyToNull(info.path("publisher").asText(null)),
                parseDate(info.path("publishedDate").asText(null)),
                pageCount != null && pageCount > 0 ? pageCount : null,
                emptyToNull(info.path("imageLinks").path("thumbnail").asText(null)),
                categories.isEmpty() ? null : String.join(", ", categories),
                emptyToNull(info.path("description").asText(null)));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.length() == 4) {
                return LocalDate.of(Integer.parseInt(value), 1, 1);
            }
            if (value.length() == 7) {
                return LocalDate.parse(value + "-01");
            }
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
