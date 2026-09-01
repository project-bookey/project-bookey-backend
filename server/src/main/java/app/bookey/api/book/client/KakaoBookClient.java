package app.bookey.api.book.client;

import app.bookey.common.config.BookeyProperties;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** 카카오 책 검색 — 1차 검색 (§F1). 페이지 수는 제공하지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoBookClient {

    private static final String SEARCH_URL = "https://dapi.kakao.com/v3/search/book";

    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    public boolean isConfigured() {
        String key = properties.bookApi().kakaoKey();
        return key != null && !key.isBlank();
    }

    public List<ExternalBook> search(String keyword, int size) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("query", keyword)
                    .queryParam("size", Math.min(size, 50))
                    .build()
                    .encode()
                    .toUri();

            JsonNode response = bookApiRestClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.bookApi().kakaoKey())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return List.of();
            }
            List<ExternalBook> results = new ArrayList<>();
            for (JsonNode doc : response.path("documents")) {
                results.add(toExternalBook(doc));
            }
            return results;
        } catch (Exception e) {
            log.warn("Kakao book search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private ExternalBook toExternalBook(JsonNode doc) {
        String isbnField = doc.path("isbn").asText("");
        String isbn13 = extractIsbn13(isbnField);
        String author = String.join(", ", asTextList(doc.path("authors")));
        String translator = String.join(", ", asTextList(doc.path("translators")));

        return new ExternalBook(
                isbn13,
                doc.path("title").asText(null),
                null,
                author.isBlank() ? null : author,
                translator.isBlank() ? null : translator,
                doc.path("publisher").asText(null),
                parseDate(doc.path("datetime").asText(null)),
                null,                                    // 카카오는 페이지 수 미제공
                emptyToNull(doc.path("thumbnail").asText(null)),
                null,
                emptyToNull(doc.path("contents").asText(null)));
    }

    static String extractIsbn13(String isbnField) {
        if (isbnField == null || isbnField.isBlank()) {
            return null;
        }
        for (String token : isbnField.split("\\s+")) {
            String digits = token.replaceAll("[^0-9Xx]", "");
            if (digits.length() == 13) {
                return digits;
            }
        }
        return null;
    }

    private static List<String> asTextList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> {
            String text = node.asText("");
            if (!text.isBlank()) {
                values.add(text);
            }
        });
        return values;
    }

    private static LocalDate parseDate(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(datetime).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
