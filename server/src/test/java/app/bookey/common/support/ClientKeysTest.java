package app.bookey.common.support;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientKeysTest {

    @Test
    @DisplayName("ipKey — ip: 뒤에 16자리 해시, 원문 IP 는 들어가지 않고 같은 IP 는 늘 같은 키")
    void ipKeyHashesAddress() {
        String key = ClientKeys.ipKey("203.0.113.5");

        assertThat(key).startsWith("ip:").hasSize(3 + 16).doesNotContain("203.0.113.5");
        assertThat(key.substring(3)).matches("[0-9a-f]{16}");
        assertThat(ClientKeys.ipKey("203.0.113.5")).isEqualTo(key);
        assertThat(ClientKeys.ipKey("203.0.113.6")).isNotEqualTo(key);
        assertThat(ClientKeys.ipKey((String) null)).startsWith("ip:");
    }

    @Test
    @DisplayName("ipKey(request) — 요청의 remoteAddr 로 만든다")
    void ipKeyFromRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("198.51.100.7");

        assertThat(ClientKeys.ipKey(request)).isEqualTo(ClientKeys.ipKey("198.51.100.7"));
    }

    @Test
    @DisplayName("isBot — 검색엔진·링크 미리보기 봇은 true, 사람 브라우저·없는 UA 는 false")
    void isBotMatchesKnownCrawlers() {
        assertThat(ClientKeys.isBot("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")).isTrue();
        assertThat(ClientKeys.isBot("Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)")).isTrue();
        assertThat(ClientKeys.isBot("facebookexternalhit/1.1")).isTrue();
        assertThat(ClientKeys.isBot("kakaotalk-scrap/1.0")).isTrue();
        assertThat(ClientKeys.isBot("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")).isFalse();
        assertThat(ClientKeys.isBot(null)).isFalse();
    }
}
