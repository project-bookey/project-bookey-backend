package app.bookey.api.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 로그로만 남기는 발송기 — 메일 인프라가 붙기 전까지의 기본 구현.
 * 로컬은 bookey.auth.email-code.expose=true 라 응답에 코드가 동봉되므로 이 로그는 보조 수단이다.
 */
@Slf4j
@Component
public class LoggingEmailCodeSender implements EmailCodeSender {

    @Override
    public void send(String email, String code, Duration ttl) {
        log.info("[이메일 인증] {} 에게 인증 코드 발송: {} (유효 {}분)", email, code, ttl.toMinutes());
    }
}
