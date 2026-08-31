package app.bookey.common.security;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_HANDLE = "handle";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final BookeyProperties.Jwt config;

    public JwtTokenProvider(BookeyProperties properties) {
        this.config = properties.jwt();
        this.key = Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createUserAccessToken(Long userId, String handle) {
        return build(userId, TokenType.USER_ACCESS, config.accessTokenTtl(),
                Map.of(CLAIM_HANDLE, handle));
    }

    public String createUserRefreshToken(Long userId) {
        return build(userId, TokenType.USER_REFRESH, config.refreshTokenTtl(), Map.of());
    }

    public String createAdminAccessToken(Long adminId, String email, String role) {
        return build(adminId, TokenType.ADMIN_ACCESS, config.adminTokenTtl(),
                Map.of(CLAIM_HANDLE, email, CLAIM_ROLE, role));
    }

    private String build(Long subject, TokenType type, Duration ttl, Map<String, ?> extra) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(subject))
                .claim(CLAIM_TYPE, type.name())
                .claims(extra)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료·토큰 타입까지 검증한다. 타입이 다르면 인증 실패로 취급한다. */
    public Claims parse(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.name().equals(claims.get(CLAIM_TYPE, String.class))) {
                throw ApiException.of(ErrorCode.INVALID_TOKEN);
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw ApiException.of(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.of(ErrorCode.INVALID_TOKEN);
        }
    }

    public Long subjectId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String handle(Claims claims) {
        return claims.get(CLAIM_HANDLE, String.class);
    }

    public String role(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public Duration refreshTtl() {
        return config.refreshTokenTtl();
    }

    public Duration accessTtl() {
        return config.accessTokenTtl();
    }
}
