package app.bookey.api.novel;

import app.bookey.common.config.AiProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Set;

/** 이미지 API 키와 모델 선택은 공통 AI 서버에만 둔다. */
@Component
public class NovelCoverAiClient {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private final AiProperties settings;
    private final RestClient client;

    @Autowired
    public NovelCoverAiClient(AiProperties settings) {
        this(settings, builder());
    }

    NovelCoverAiClient(AiProperties settings, RestClient.Builder builder) {
        this.settings = settings;
        this.client = builder.baseUrl(settings.baseUrl()).build();
    }

    private static RestClient.Builder builder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // 설명 작성 30초와 이미지 생성 120초를 기다린다.
        factory.setReadTimeout(Duration.ofSeconds(160));
        return RestClient.builder().requestFactory(factory);
    }

    public record ImageAvailability(boolean available) {}
    public record GeneratedCoverImage(byte[] data, String contentType) {}

    public boolean available() {
        if (!configured()) return false;
        try {
            ImageAvailability response = client.get().uri("/v1/cover-config")
                    .header("X-API-Key", settings.apiKey()).retrieve().body(ImageAvailability.class);
            return response != null && response.available();
        } catch (RestClientException exception) {
            return false;
        }
    }

    public GeneratedCoverImage generate(NovelCoverGenerationService.NovelCoverGenerationRequest body) {
        if (!configured()) throw ApiException.of(ErrorCode.AI_COVER_UNAVAILABLE);
        try {
            return client.post().uri("/v1/covers").header("X-API-Key", settings.apiKey()).body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 429)
                            throw ApiException.of(ErrorCode.AI_COVER_LIMIT);
                        if (!response.getStatusCode().is2xxSuccessful())
                            throw ApiException.of(ErrorCode.AI_COVER_UNAVAILABLE);
                        var type = response.getHeaders().getContentType();
                        String mime = type == null ? "" : type.getType() + "/" + type.getSubtype();
                        if (!TYPES.contains(mime)) throw ApiException.of(ErrorCode.AI_COVER_UNAVAILABLE);
                        byte[] data = response.getBody().readNBytes(MAX_BYTES + 1);
                        if (data.length == 0 || data.length > MAX_BYTES)
                            throw ApiException.of(ErrorCode.AI_COVER_UNAVAILABLE);
                        return new GeneratedCoverImage(data, mime);
                    });
        } catch (RestClientException exception) {
            // 외부 응답이나 인증 정보를 앱으로 전달하지 않는다.
            throw ApiException.of(ErrorCode.AI_COVER_UNAVAILABLE);
        }
    }

    private boolean configured() {
        return settings.apiKey() != null && !settings.apiKey().isBlank();
    }
}
