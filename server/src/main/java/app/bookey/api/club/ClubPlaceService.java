package app.bookey.api.club;

import app.bookey.common.config.BookeyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubPlaceService {

    private static final String SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final ClubService clubService;
    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    public record PlaceView(String id, String name, String address, String roadAddress,
                            double latitude, double longitude, String phone, String mapUrl) {}

    public List<PlaceView> search(Long userId, Long clubId, String query) {
        clubService.activeMember(clubId, userId);
        if (query == null || query.trim().length() < 2 || properties.bookApi().kakaoKey().isBlank()) {
            return List.of();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("query", query.trim())
                    .queryParam("size", 15)
                    .build().encode().toUri();
            JsonNode response = bookApiRestClient.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.bookApi().kakaoKey())
                    .retrieve().body(JsonNode.class);
            if (response == null) return List.of();
            List<PlaceView> result = new ArrayList<>();
            for (JsonNode place : response.path("documents")) {
                result.add(new PlaceView(
                        place.path("id").asText(), place.path("place_name").asText(),
                        place.path("address_name").asText(), place.path("road_address_name").asText(),
                        place.path("y").asDouble(), place.path("x").asDouble(),
                        place.path("phone").asText(), place.path("place_url").asText()));
            }
            return result;
        } catch (Exception e) {
            log.warn("모임 약속 장소 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }
}
