package app.bookey.api.auth;

import app.bookey.common.config.BookeyProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bookey.mail", name = "enabled", havingValue = "true")
public class SmtpEmailCodeSender implements EmailCodeSender {

    private final JavaMailSender mailSender;
    private final BookeyProperties properties;

    @Override
    public void send(String email, String code, Duration ttl) {
        BookeyProperties.Mail mail = properties.mail();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mail.from(), mail.fromName());
            helper.setTo(email);
            helper.setSubject(mail.subjectPrefix() + " 이메일 인증 코드");
            helper.setText(textBody(code, ttl), htmlBody(code, ttl));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("이메일 인증 코드 메일을 만들 수 없습니다.", e);
        }
    }

    private static String textBody(String code, Duration ttl) {
        return """
                Bookey 이메일 인증 코드입니다.

                인증 코드: %s
                유효 시간: %d분

                본인이 요청하지 않았다면 이 메일을 무시하세요.
                """.formatted(code, ttl.toMinutes());
    }

    private static String htmlBody(String code, Duration ttl) {
        return """
                <!doctype html>
                <html lang="ko">
                  <body style="margin:0;padding:0;background:#faf8f4;color:#1a1c18;font-family:Arial,'Apple SD Gothic Neo','Malgun Gothic',sans-serif">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#faf8f4;margin:0;padding:32px 16px">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="width:100%%;max-width:520px;background:#ffffff;border:1px solid #e5e1d7;border-radius:10px;overflow:hidden">
                            <tr>
                              <td style="background:#123528;padding:24px 28px;border-bottom:4px solid #3ddc97">
                                <div style="font-size:11px;line-height:16px;letter-spacing:2px;text-transform:uppercase;color:#ddf2e7;font-weight:700">Bookey</div>
                                <h1 style="margin:8px 0 0;font-size:24px;line-height:32px;color:#ffffff;font-weight:800">이메일 인증 코드</h1>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:28px">
                                <p style="margin:0 0 18px;font-size:15px;line-height:24px;color:#57554f">아래 인증 코드를 입력해 Bookey 가입을 완료하세요.</p>
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:0 0 20px;background:#ddf2e7;border:1px solid #b9dfce;border-radius:8px">
                                  <tr>
                                    <td align="center" style="padding:22px 16px">
                                      <div style="font-family:'Courier New',Courier,monospace;font-size:34px;line-height:42px;letter-spacing:6px;color:#177a54;font-weight:700">%s</div>
                                    </td>
                                  </tr>
                                </table>
                                <p style="margin:0 0 20px;font-size:14px;line-height:22px;color:#57554f">이 코드는 <strong style="color:#1a1c18">%d분</strong> 동안 유효합니다.</p>
                                <div style="height:1px;background:#e5e1d7;margin:0 0 18px"></div>
                                <p style="margin:0;font-size:12px;line-height:20px;color:#8b887f">본인이 요청하지 않았다면 이 메일을 무시하세요.</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(code, ttl.toMinutes());
    }
}
