package app.bookey.common.security;

import app.bookey.common.config.BookeyProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 발급 규칙 단위 테스트 — 같은 초 안에 연속 발급해도 토큰이 겹치지 않아야 한다.
 * iat·exp 는 초 단위라 sub·typ 만으로는 같은 초의 토큰이 바이트 단위로 같아지고,
 * 그 sha256 을 유니크 키로 저장하는 refresh_tokens 에서 충돌(500)이 났다.
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(new BookeyProperties(
            new BookeyProperties.Jwt("unit-test-secret-must-be-at-least-32-bytes-long",
                    Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(30)),
            null, null, null, null));

    @Test
    @DisplayName("같은 사용자에게 같은 초 안에 연속 발급한 리프레시 토큰은 서로 다르고, 각각 jti 를 가진다")
    void refreshTokensIssuedInSameSecondDiffer() {
        // 세 번 연속 발급하면 초 경계를 한 번 넘더라도 최소 두 개는 같은 초에 발급된다.
        List<String> tokens = List.of(
                provider.createUserRefreshToken(1L),
                provider.createUserRefreshToken(1L),
                provider.createUserRefreshToken(1L));

        assertThat(tokens).doesNotHaveDuplicates();
        List<Claims> claims = tokens.stream().map(t -> provider.parse(t, TokenType.USER_REFRESH)).toList();
        assertThat(claims).allSatisfy(c -> {
            assertThat(c.getId()).isNotBlank();
            assertThat(provider.subjectId(c)).isEqualTo(1L);
        });
        assertThat(claims).extracting(Claims::getId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("액세스 토큰(사용자·관리자)도 연속 발급 시 서로 다르고 기존 클레임(handle·role)은 그대로다")
    void accessTokensIssuedInSameSecondDiffer() {
        List<String> userTokens = List.of(
                provider.createUserAccessToken(1L, "tester1"),
                provider.createUserAccessToken(1L, "tester1"),
                provider.createUserAccessToken(1L, "tester1"));
        List<String> adminTokens = List.of(
                provider.createAdminAccessToken(9L, "admin@bookey.local", "SUPER"),
                provider.createAdminAccessToken(9L, "admin@bookey.local", "SUPER"),
                provider.createAdminAccessToken(9L, "admin@bookey.local", "SUPER"));

        assertThat(userTokens).doesNotHaveDuplicates();
        assertThat(adminTokens).doesNotHaveDuplicates();

        Claims user = provider.parse(userTokens.get(0), TokenType.USER_ACCESS);
        assertThat(provider.subjectId(user)).isEqualTo(1L);
        assertThat(provider.handle(user)).isEqualTo("tester1");
        Claims admin = provider.parse(adminTokens.get(0), TokenType.ADMIN_ACCESS);
        assertThat(provider.subjectId(admin)).isEqualTo(9L);
        assertThat(provider.handle(admin)).isEqualTo("admin@bookey.local");
        assertThat(provider.role(admin)).isEqualTo("SUPER");
    }
}
