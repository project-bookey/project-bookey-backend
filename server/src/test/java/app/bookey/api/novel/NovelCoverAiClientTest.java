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

class NovelCoverAiClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final NovelCoverAiClient client = new NovelCoverAiClient(new AiProperties("http://ai", "server-secret"), builder);
    private final NovelCoverGenerationService.Request request = new NovelCoverGenerationService.Request("달빛", "밤의 가게", "판타지");

    @Test void forwardsMetadataAndReturnsBinaryImage() {
        byte[] image = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47};
        server.expect(requestTo("http://ai/v1/covers"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-API-Key", "server-secret"))
                .andExpect(content().json("{\"title\":\"달빛\",\"description\":\"밤의 가게\",\"genre\":\"판타지\"}"))
                .andRespond(withSuccess(image, MediaType.IMAGE_PNG));
        var result = client.generate(request);
        assertThat(result.data()).isEqualTo(image);
        assertThat(result.contentType()).isEqualTo("image/png");
        server.verify();
    }

    @Test void refusesNonImageSuccessAndDoesNotExposeResponse() {
        server.expect(requestTo("http://ai/v1/covers"))
                .andRespond(withSuccess("server-secret", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.generate(request)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_COVER_UNAVAILABLE);
            assertThat(error.getMessage()).doesNotContain("server-secret");
        });
    }

    @Test void mapsRateLimitWithoutLeakingResponse() {
        server.expect(requestTo("http://ai/v1/covers"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("server-secret"));
        assertThatThrownBy(() -> client.generate(request)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_COVER_LIMIT));
    }

    @Test void rejectsOversizedImage() {
        server.expect(requestTo("http://ai/v1/covers"))
                .andRespond(withSuccess(new byte[10 * 1024 * 1024 + 1], MediaType.IMAGE_PNG));
        assertThatThrownBy(() -> client.generate(request)).isInstanceOf(ApiException.class);
    }

    @Test void unconfiguredClientDoesNotCallAi() {
        var disabled = new NovelCoverAiClient(new AiProperties("http://ai", ""), builder);
        assertThat(disabled.available()).isFalse();
        assertThatThrownBy(() -> disabled.generate(request)).isInstanceOf(ApiException.class);
        server.verify();
    }
}
