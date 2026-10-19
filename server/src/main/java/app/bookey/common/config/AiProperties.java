package app.bookey.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 서버 전용 AI 연결. 이 인증키는 앱에 전달하지 않는다. */
@ConfigurationProperties(prefix = "bookey.ai")
public record AiProperties(String baseUrl, String apiKey) {
    @Override public String toString() { return "AiProperties[credentials=redacted]"; }
}
