package app.bookey.api.auth;

import app.bookey.common.config.BookeyProperties;
import app.bookey.domain.user.EmailCodePurpose;
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

    /** 용도별 문구 — 제목, 안내 문장, 요청하지 않은 사람에게 주는 안심 문장. */
    private record Copy(String title, String lead, String ignore) {}

    private static Copy copyFor(EmailCodePurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> new Copy("이메일 인증 코드",
                    "아래 코드를 앱에 입력하면 가입을 마칠 수 있어요.",
                    "직접 요청하지 않았다면 이 메일은 무시해 주세요.");
            case PASSWORD_RESET -> new Copy("비밀번호 재설정 코드",
                    "아래 코드를 앱에 입력하고 새 비밀번호를 정해 주세요.",
                    "직접 요청하지 않았다면 무시해 주세요. 비밀번호는 바뀌지 않아요.");
        };
    }

    @Override
    public void send(String email, String code, Duration ttl, EmailCodePurpose purpose) {
        BookeyProperties.Mail mail = properties.mail();
        Copy copy = copyFor(purpose);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mail.from(), mail.fromName());
            helper.setTo(email);
            helper.setSubject(mail.subjectPrefix() + " " + copy.title());
            helper.setText(textBody(copy, code, ttl), htmlBody(copy, code, ttl));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("이메일 인증 코드 메일을 만들 수 없습니다.", e);
        }
    }

    private static String textBody(Copy copy, String code, Duration ttl) {
        return """
                Bookey · %s
                %s

                코드: %s
                쓸 수 있는 시간: %d분

                %s
                """.formatted(copy.title(), copy.lead(), code, ttl.toMinutes(), copy.ignore());
    }

    private static String htmlBody(Copy copy, String code, Duration ttl) {
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
                                <h1 style="margin:8px 0 0;font-size:24px;line-height:32px;color:#ffffff;font-weight:800">%s</h1>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:28px">
                                <p style="margin:0 0 18px;font-size:15px;line-height:24px;color:#57554f">%s</p>
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:0 0 20px;background:#ddf2e7;border:1px solid #b9dfce;border-radius:8px">
                                  <tr>
                                    <td align="center" style="padding:22px 16px">
                                      <div style="font-family:'Courier New',Courier,monospace;font-size:34px;line-height:42px;letter-spacing:6px;color:#177a54;font-weight:700">%s</div>
                                    </td>
                                  </tr>
                                </table>
                                <p style="margin:0 0 20px;font-size:14px;line-height:22px;color:#57554f">이 코드는 <strong style="color:#1a1c18">%d분</strong> 동안 쓸 수 있어요.</p>
                                <div style="height:1px;background:#e5e1d7;margin:0 0 18px"></div>
                                <p style="margin:0;font-size:12px;line-height:20px;color:#8b887f">%s</p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(copy.title(), copy.lead(), code, ttl.toMinutes(), copy.ignore());
    }
}
