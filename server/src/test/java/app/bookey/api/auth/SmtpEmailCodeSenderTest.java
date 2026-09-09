package app.bookey.api.auth;

import app.bookey.common.config.BookeyProperties;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailCodeSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    @DisplayName("SMTP 발송기 — 인증 코드 메일의 수신자·제목·본문을 채워 전송한다")
    void sendsVerificationCodeEmail() throws Exception {
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);
        BookeyProperties properties = new BookeyProperties(
                null, null, null, null, null, null, null, null, null,
                new BookeyProperties.Mail(true, "no-reply@bookey.app", "Bookey", "[Bookey]"));

        new SmtpEmailCodeSender(mailSender, properties)
                .send("reader@example.com", "123456", Duration.ofMinutes(10));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage sentMessage = sent.getValue();
        assertThat(sentMessage.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("reader@example.com");
        assertThat(sentMessage.getFrom()[0].toString())
                .contains("no-reply@bookey.app");
        assertThat(sentMessage.getSubject()).isEqualTo("[Bookey] 이메일 인증 코드");
        assertThat(contentOf(sentMessage))
                .contains("123456")
                .contains("#faf8f4")
                .contains("#177a54")
                .contains("#ddf2e7");
    }

    private static String contentOf(Part part) throws IOException, jakarta.mail.MessagingException {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                out.append(contentOf(multipart.getBodyPart(i)));
            }
            return out.toString();
        }
        return "";
    }
}
