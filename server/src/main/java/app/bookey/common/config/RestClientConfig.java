package app.bookey.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 외부 도서 API 호출용. 클라이언트는 외부 API 를 직접 호출하지 않는다(§F1). */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient bookApiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
