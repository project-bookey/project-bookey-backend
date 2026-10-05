package app.bookey.api.club;

import app.bookey.api.club.ClubPlaceService.AddressView;
import app.bookey.common.config.BookeyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 모임 장소 주소 검색 — 도로명주소 검색 결과에 카카오 좌표를 붙이고, 덜 적은 검색어 오류는 빈 결과로 둔다. */
class ClubPlaceServiceTest {

    private static final String JUSO = "https://business.juso.go.kr/addrlink/addrLinkApi.do";
    private static final String KAKAO_ADDRESS = "https://dapi.kakao.com/v2/local/search/address.json";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

    private ClubPlaceService service(String jusoKey) {
        BookeyProperties properties = new BookeyProperties(null, null,
                new BookeyProperties.BookApi("kakao-key", null, null, null, null, null, jusoKey),
                null, null, null, null, null, null, null);
        return new ClubPlaceService(mock(ClubService.class), builder.build(), properties);
    }

    private static String kakaoAddress(String road, double lat, double lng) {
        return """
                {"documents":[{"address_name":"%s","x":"%s","y":"%s",
                  "address":{"address_name":"지번"},"road_address":{"address_name":"%s","building_name":"","zone_no":"04036"}}]}
                """.formatted(road, lng, lat, road);
    }

    @Test
    @DisplayName("도로명주소 검색이 있으면 덜 적은 주소에도 관련 주소를 내주고, 결과마다 카카오 좌표를 붙인다")
    void jusoResultsGetKakaoCoordinates() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(JUSO)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("keyword", "%EC%96%91%ED%99%94%EB%A1%9C%204"))
                .andRespond(withSuccess("""
                        {"results":{"common":{"errorCode":"0","errorMessage":"정상","totalCount":"2"},
                          "juso":[
                            {"roadAddrPart1":"서울특별시 마포구 양화로 45","jibunAddr":"서울특별시 마포구 서교동 395-166 메세나폴리스",
                             "bdNm":"메세나폴리스","zipNo":"04036"},
                            {"roadAddrPart1":"서울특별시 마포구 양화로 40","jibunAddr":"서울특별시 마포구 서교동 395-1",
                             "bdNm":"","zipNo":"04037"}]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.containsString("%EC%96%91%ED%99%94%EB%A1%9C%2045")))
                .andRespond(withSuccess(kakaoAddress("서울 마포구 양화로 45", 37.5494, 126.9145), MediaType.APPLICATION_JSON));
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.containsString("%EC%96%91%ED%99%94%EB%A1%9C%2040")))
                .andRespond(withSuccess(kakaoAddress("서울 마포구 양화로 40", 37.5490, 126.9150), MediaType.APPLICATION_JSON));

        List<AddressView> found = service("juso-key").searchAddresses(1L, 10L, "양화로 4");

        assertThat(found).extracting(AddressView::roadAddress)
                .containsExactly("서울특별시 마포구 양화로 45", "서울특별시 마포구 양화로 40");
        assertThat(found.get(0).buildingName()).isEqualTo("메세나폴리스");
        assertThat(found.get(0).latitude()).isEqualTo(37.5494);
        assertThat(found.get(1).longitude()).isEqualTo(126.9150);
    }

    @Test
    @DisplayName("카카오가 막혀 있으면(카카오맵 미활성 403) 주소는 좌표 없이 내고, 한동안 카카오를 다시 부르지 않는다")
    void kakaoForbiddenStillReturnsAddresses() {
        String juso = """
                {"results":{"common":{"errorCode":"0","errorMessage":"정상","totalCount":"1"},
                  "juso":[{"roadAddrPart1":"서울특별시 마포구 양화로 45","jibunAddr":"서울특별시 마포구 서교동 395-166",
                           "bdNm":"메세나폴리스","zipNo":"04036"}]}}
                """;
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.startsWith(JUSO)))
                .andRespond(withSuccess(juso, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(KAKAO_ADDRESS)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorType\":\"NotAuthorizedError\",\"message\":\"App(bookey) disabled OPEN_MAP_AND_LOCAL service.\"}"));
        ClubPlaceService service = service("juso-key");

        List<AddressView> first = service.searchAddresses(1L, 10L, "양화로 45");
        List<AddressView> second = service.searchAddresses(1L, 10L, "양화로 45");

        assertThat(first).singleElement().satisfies(a -> {
            assertThat(a.roadAddress()).isEqualTo("서울특별시 마포구 양화로 45");
            assertThat(a.latitude()).isNull();
        });
        assertThat(second).hasSize(1);
        server.verify(); // 카카오는 한 번만 불렸다
    }

    @Test
    @DisplayName("너무 넓은 검색어처럼 도로명주소 검색이 오류 코드를 주면 빈 결과다")
    void jusoErrorIsEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(JUSO)))
                .andRespond(withSuccess("""
                        {"results":{"common":{"errorCode":"E0006","errorMessage":"주소를 상세히 입력해 주시기 바랍니다."},"juso":null}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service("juso-key").searchAddresses(1L, 10L, "서울")).isEmpty();
    }

    @Test
    @DisplayName("도로명주소 키가 없으면 카카오 주소 검색만 쓴다")
    void withoutJusoKeyUsesKakaoAddress() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(KAKAO_ADDRESS)))
                .andRespond(withSuccess(kakaoAddress("서울 마포구 양화로 45", 37.5494, 126.9145), MediaType.APPLICATION_JSON));

        List<AddressView> found = service("").searchAddresses(1L, 10L, "양화로 45");

        assertThat(found).singleElement().satisfies(a -> {
            assertThat(a.roadAddress()).isEqualTo("서울 마포구 양화로 45");
            assertThat(a.address()).isEqualTo("지번");
        });
        server.verify();
    }
}
