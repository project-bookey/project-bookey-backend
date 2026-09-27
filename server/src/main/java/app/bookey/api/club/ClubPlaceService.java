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
    private static final String GEOCODE_URL = "https://nominatim.openstreetmap.org/search";

    private final ClubService clubService;
    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;

    public record PlaceView(String id, String name, String address, String roadAddress,
                            double latitude, double longitude, String phone, String mapUrl) {}
    public record Coordinates(double latitude, double longitude) {}
    public record AddressView(String address, String roadAddress, String buildingName, String zonecode,
                              double latitude, double longitude) {}

    /** 검색 버튼을 누른 경우에만 호출한다. 공개 Nominatim 정책상 자동완성 요청에는 사용하지 않는다. */
    public List<AddressView> searchAddresses(Long userId, Long clubId, String query) {
        clubService.activeMember(clubId, userId);
        if (query == null || query.trim().length() < 2) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(GEOCODE_URL)
                    .queryParam("q", query.trim()).queryParam("format", "jsonv2")
                    .queryParam("countrycodes", "kr").queryParam("limit", 5)
                    .queryParam("addressdetails", 1).queryParam("namedetails", 1)
                    .queryParam("accept-language", "ko")
                    .build().encode().toUri();
            JsonNode response = bookApiRestClient.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, "Bookey/0.1 (https://bookey.site)")
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.isArray()) return List.of();
            List<AddressView> result = new ArrayList<>();
            for (JsonNode item : response) {
                String display = item.path("display_name").asText();
                String name = item.path("namedetails").path("name").asText("");
                String postcode = item.path("address").path("postcode").asText("");
                result.add(new AddressView(display, display, name, postcode,
                        item.path("lat").asDouble(), item.path("lon").asDouble()));
            }
            return result;
        } catch (Exception e) {
            log.warn("주소 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /** 우편번호 검색에서 사용자가 확정한 주소만 1회 좌표로 바꾼다. 자동완성 용도로 호출하지 않는다. */
    public Coordinates geocode(Long userId, Long clubId, String address) {
        clubService.activeMember(clubId, userId);
        if (address == null || address.isBlank()) return null;
        try {
            URI uri = UriComponentsBuilder.fromUriString(GEOCODE_URL)
                    .queryParam("q", address.trim()).queryParam("format", "jsonv2")
                    .queryParam("countrycodes", "kr").queryParam("limit", 1)
                    .build().encode().toUri();
            JsonNode response = bookApiRestClient.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, "Bookey/0.1 (https://bookey.site)")
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.isArray() || response.isEmpty()) return null;
            JsonNode first = response.get(0);
            return new Coordinates(first.path("lat").asDouble(), first.path("lon").asDouble());
        } catch (Exception e) {
            log.warn("확정 주소 좌표 변환 실패: {}", e.getMessage());
            return null;
        }
    }

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
