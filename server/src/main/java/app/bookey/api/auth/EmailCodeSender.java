package app.bookey.api.auth;

import app.bookey.domain.user.EmailCodePurpose;

import java.time.Duration;

/** 이메일 인증 코드 발송 채널(가입·비밀번호 재설정). 운영 메일 발송(SMTP/발송 서비스)이 붙으면 구현을 교체한다. */
public interface EmailCodeSender {

    void send(String email, String code, Duration ttl, EmailCodePurpose purpose);
}
