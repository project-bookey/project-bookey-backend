package app.bookey.api.auth;

import java.time.Duration;

/** 가입 인증 코드 발송 채널. 운영 메일 발송(SMTP/발송 서비스)이 붙으면 구현을 교체한다. */
public interface EmailCodeSender {

    void send(String email, String code, Duration ttl);
}
