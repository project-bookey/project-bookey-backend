package app.bookey.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 시간 의존 로직(무료 엽서 KST 리셋, 구독 판정)이 테스트에서 고정 시계를 쓸 수 있게 빈으로 둔다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
