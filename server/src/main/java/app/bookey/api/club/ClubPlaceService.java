package app.bookey.api.club;

import app.bookey.common.config.BookeyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubPlaceService {

    private static final String SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json";
    private static final String JUSO_URL = "https://business.juso.go.kr/addrlink/addrLinkApi.do";
    /** 도로명주소 검색이 막는 글자(특수문자) — 보내면 오류가 오니 빈칸으로 바꿔 보낸다. */
    private static final String JUSO_BLOCKED = "[%=><\\[\\]]";
    private static final int ADDRESS_LIMIT = 10;
    /** 주소 → 좌표 캐시 크기. 한 자씩 적을 때 앞 글자 결과와 겹치는 주소가 많아 카카오 호출이 크게 준다. */
    private static final int COORDINATE_CACHE_SIZE = 5_000;
    /** 카카오가 키·권한 오류(401/403 — 카카오맵 미활성 등)를 주면 이만큼 좌표 붙이기를 쉰다 — 한 자마다 실패 호출 10번을 막는다. */
    private static final Duration KAKAO_BACKOFF = Duration.ofMinutes(5);
    private static final String GEOCODE_URL = "https://nominatim.openstreetmap.org/search";

    private final ClubService clubService;
    private final RestClient bookApiRestClient;
    private final BookeyProperties properties;
    /** 도로명주소 결과마다 좌표를 붙이는 카카오 호출 — 기다리기만 하는 일이라 가상 스레드로 한꺼번에 보낸다. */
    private final ExecutorService geocodeExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Coordinates> coordinateCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Coordinates> eldest) {
                    return size() > COORDINATE_CACHE_SIZE;
                }
            });
    private volatile Instant kakaoBlockedUntil = Instant.MIN;

    public record PlaceView(String id, String name, String address, String roadAddress,
                            double latitude, double longitude, String phone, String mapUrl) {}
    public record Coordinates(double latitude, double longitude) {}
    /** 좌표는 못 붙일 수 있다(null) — 도로명주소 검색은 좌표를 주지 않고, 카카오가 막히면 붙이지 못한다. 앱은 고를 때 geocode 로 다시 묻는다. */
    public record AddressView(String address, String roadAddress, String buildingName, String zonecode,
                              Double latitude, Double longitude) {}

    /**
     * 주소 검색 — 앱이 장소 이름 검색과 함께 한 자씩 적을 때마다(짧은 디바운스) 부른다. 그래서 자동완성을 금하는 공개
     * Nominatim 은 쓰지 않는다. 도로명주소 검색(행정안전부, JUSO_API_KEY)이 있으면 그것으로 '양화로 4'처럼 덜 적은 주소에도
     * 관련 주소를 내주고, 좌표는 결과마다 카카오 주소 검색으로 붙인다 — 카카오가 막혀도 주소는 좌표 없이 낸다.
     * 도로명주소 키가 없으면 카카오 주소 검색만 — 다 적은 주소만 찾는다.
     */
    public List<AddressView> searchAddresses(Long userId, Long clubId, String query) {
        clubService.activeMember(clubId, userId);
        if (query == null || query.trim().length() < 2) return List.of();
        String jusoKey = properties.bookApi().jusoKey();
        if (jusoKey != null && !jusoKey.isBlank()) return searchJuso(jusoKey, query.trim());
        return kakaoReady() ? searchKakaoAddress(query.trim(), 5) : List.of();
    }

    private boolean kakaoReady() {
        return !properties.bookApi().kakaoKey().isBlank() && Instant.now().isAfter(kakaoBlockedUntil);
    }

    private List<AddressView> searchJuso(String key, String query) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(JUSO_URL)
                    .queryParam("confmKey", key)
                    .queryParam("currentPage", 1)
                    .queryParam("countPerPage", ADDRESS_LIMIT)
                    .queryParam("keyword", query.replaceAll(JUSO_BLOCKED, " ").trim())
                    .queryParam("resultType", "json")
                    .build().encode().toUri();
            JsonNode results = Objects.requireNonNull(bookApiRestClient.get().uri(uri).retrieve().body(JsonNode.class))
                    .path("results");
            // 너무 넓은 검색어('서울')·한 글자 같은 것은 오류 코드로 온다 — 덜 적은 상태일 뿐이니 빈 결과로 둔다.
            String errorCode = results.path("common").path("errorCode").asText("");
            if (!"0".equals(errorCode)) {
                if (errorCode.equals("E0001") || errorCode.equals("E0014")) {
                    log.warn("도로명주소 검색 키 오류: {} {}", errorCode, results.path("common").path("errorMessage").asText(""));
                }
                return List.of();
            }
            List<CompletableFuture<AddressView>> located = new ArrayList<>();
            for (JsonNode juso : results.path("juso")) {
                String road = juso.path("roadAddrPart1").asText("");
                String jibun = juso.path("jibunAddr").asText("");
                String building = juso.path("bdNm").asText("");
                String zip = juso.path("zipNo").asText("");
                located.add(CompletableFuture.supplyAsync(() -> {
                    Coordinates at = coordinatesOf(road);
                    return new AddressView(jibun, road, building, zip,
                            at == null ? null : at.latitude(), at == null ? null : at.longitude());
                }, geocodeExecutor));
            }
            return located.stream().map(CompletableFuture::join).toList();
        } catch (Exception e) {
            log.warn("도로명주소 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /** 도로명주소 한 줄의 좌표 — 다 적은 주소라 카카오 주소 검색 첫 결과가 곧 그 건물이다. */
    private Coordinates coordinatesOf(String roadAddress) {
        if (roadAddress.isBlank() || !kakaoReady()) return null;
        Coordinates cached = coordinateCache.get(roadAddress);
        if (cached != null) return cached;
        List<AddressView> found = searchKakaoAddress(roadAddress, 1);
        if (found.isEmpty()) return null;
        Coordinates at = new Coordinates(found.get(0).latitude(), found.get(0).longitude());
        coordinateCache.put(roadAddress, at);
        return at;
    }

    private List<AddressView> searchKakaoAddress(String query, int size) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(ADDRESS_URL)
                    .queryParam("query", query)
                    .queryParam("size", size)
                    .build().encode().toUri();
            JsonNode response = bookApiRestClient.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.bookApi().kakaoKey())
                    .retrieve().body(JsonNode.class);
            if (response == null) return List.of();
            List<AddressView> result = new ArrayList<>();
            for (JsonNode item : response.path("documents")) {
                // 지번 주소(address)와 도로명 주소(road_address)는 둘 중 하나가 null 일 수 있다 — 동 이름만 찾으면 도로명이 없다.
                JsonNode road = item.path("road_address");
                String address = item.path("address").path("address_name").asText(item.path("address_name").asText());
                result.add(new AddressView(address, road.path("address_name").asText(""),
                        road.path("building_name").asText(""), road.path("zone_no").asText(""),
                        item.path("y").asDouble(), item.path("x").asDouble()));
            }
            return result;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // 키가 틀렸거나 앱에 카카오맵이 꺼져 있다(disabled OPEN_MAP_AND_LOCAL service) — 한동안 부르지 않는다.
            kakaoBlockedUntil = Instant.now().plus(KAKAO_BACKOFF);
            log.warn("카카오 주소 검색 권한 오류 — {} 동안 쉼: {}", KAKAO_BACKOFF, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("주소 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 사용자가 고른 주소 하나를 좌표로 — 목록에서 좌표를 못 붙인 주소를 고를 때 앱이 한 번 부른다. 카카오가 먼저,
     * 안 되면 Nominatim(확정한 주소 1회 조회라 정책에 맞다). 자동완성 용도로 호출하지 않는다.
     */
    public Coordinates geocode(Long userId, Long clubId, String address) {
        clubService.activeMember(clubId, userId);
        if (address == null || address.isBlank()) return null;
        Coordinates kakao = coordinatesOf(address.trim());
        if (kakao != null) return kakao;
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
