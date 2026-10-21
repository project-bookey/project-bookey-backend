package app.bookey.api.novel;

import app.bookey.common.config.AiProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NovelAiClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final NovelAiClient client = new NovelAiClient(new AiProperties("http://ai", "private-key"), builder);

    @Test void forwardsOnlySelectedTextAndServerKey() {
        server.expect(requestTo("http://ai/v1/rewrites"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "private-key"))
                .andExpect(content().json("{\"text\":\"그가 달려갓다.\"}"))
                .andRespond(withSuccess("{\"text\":\"그가 달려갔다.\"}", MediaType.APPLICATION_JSON));
        assertThat(client.rewrite("그가 달려갓다.").text()).isEqualTo("그가 달려갔다.");
        server.verify();
    }

    @Test void mapsQuotaFailureWithoutLeakingUpstreamMessage() {
        server.expect(requestTo("http://ai/v1/rewrites"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("private-key"));
        assertThatThrownBy(() -> client.rewrite("원고"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_RATE_LIMITED);
                    assertThat(error.getMessage()).doesNotContain("private-key");
                });
    }

    @Test void rejectsInvalidSuccessfulResponse() {
        server.expect(requestTo("http://ai/v1/rewrites"))
                .andRespond(withSuccess("{\"text\":\" \"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.rewrite("원고"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    @Test void missingKeyDoesNotCallUpstream() {
        var unconfigured = new NovelAiClient(new AiProperties("http://ai", ""), builder);
        assertThatThrownBy(() -> unconfigured.rewrite("원고")).isInstanceOf(ApiException.class);
        server.verify();
    }

    @Test void storyValidatesNestedResponseAndMode() {
        server.expect(requestTo("http://ai/v1/story-suggestions"))
                .andExpect(header("X-API-Key", "private-key"))
                .andExpect(content().json("{\"mode\":\"OPENING\",\"text\":\"소재\"}"))
                .andRespond(withSuccess("{\"suggestions\":[{\"title\":\"편지\",\"text\":\"비가 내렸다.\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.story("OPENING", "소재").suggestions().getFirst().text()).isEqualTo("비가 내렸다.");
        server.verify();
    }

    @Test void wrongPlotCountIsRejected() {
        server.expect(requestTo("http://ai/v1/story-suggestions"))
                .andRespond(withSuccess("{\"suggestions\":[{\"title\":\"하나\",\"text\":\"단 하나의 전개.\"}]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.story("PLOT", "원고")).isInstanceOf(ApiException.class);
    }
}
