package app.bookey.api.novel;

import app.bookey.common.config.AiProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.List;

@Component
public class NovelAiClient {
    private final AiProperties settings;
    private final RestClient client;

    @Autowired
    public NovelAiClient(AiProperties settings) {
        this(settings, builder());
    }

    NovelAiClient(AiProperties settings, RestClient.Builder builder) {
        this.settings = settings;
        this.client = builder.baseUrl(settings.baseUrl()).build();
    }

    private static RestClient.Builder builder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(35));
        return RestClient.builder().requestFactory(factory);
    }

    public record Rewrite(String text) {}
    public record StoryOption(String title, String text) {}
    public record Story(List<StoryOption> suggestions) {}

    public Rewrite rewrite(String text) {
        Rewrite response = post("/v1/rewrites", Map.of("text", text), Rewrite.class);
        if (response == null || response.text() == null || response.text().isBlank()
                || response.text().length() > 3000)
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        return response;
    }

    public Story story(String mode, String context) {
        Story response = post("/v1/story-suggestions", Map.of("mode", mode, "text", context), Story.class);
        int expected = "PLOT".equals(mode) ? 3 : 1;
        if (response == null || response.suggestions() == null || response.suggestions().size() != expected
                || response.suggestions().stream().anyMatch(option -> option == null
                || option.title() == null || option.title().isBlank() || option.title().length() > 40
                || option.text() == null || option.text().isBlank() || option.text().length() > 700))
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        return response;
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        if (settings.apiKey() == null || settings.apiKey().isBlank())
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        try {
            return client.post().uri(path)
                    .header("X-API-Key", settings.apiKey())
                    .body(body).retrieve().body(responseType);
        } catch (RestClientResponseException exception) {
            // 원격 응답과 인증 정보는 사용자 메시지나 로그로 전달하지 않는다.
            if (exception.getStatusCode().value() == 429)
                throw ApiException.of(ErrorCode.AI_RATE_LIMITED);
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
    }
}
