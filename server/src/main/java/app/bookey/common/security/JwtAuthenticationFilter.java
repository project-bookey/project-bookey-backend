package app.bookey.common.security;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.error.ErrorResponse;
import app.bookey.domain.admin.AdminRole;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer 토큰을 검증해 SecurityContext 를 채운다.
 * 서비스용/관리자용 필터를 같은 클래스로 쓰되 기대 토큰 타입이 다르므로,
 * 서비스 JWT 로는 /admin/v1/** 에 절대 접근할 수 없다(§F13 보안 요구사항).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final TokenType expectedType;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = tokenProvider.parse(token, expectedType);
            Long id = tokenProvider.subjectId(claims);

            if (expectedType == TokenType.ADMIN_ACCESS) {
                AdminRole role = AdminRole.valueOf(tokenProvider.role(claims));
                AuthAdmin principal = new AuthAdmin(id, tokenProvider.handle(claims), role);
                setAuthentication(principal, "ROLE_ADMIN_" + role.name());
            } else {
                AuthUser principal = new AuthUser(id, tokenProvider.handle(claims));
                setAuthentication(principal, "ROLE_USER");
            }
        } catch (ApiException e) {
            SecurityContextHolder.clearContext();
            writeError(response, e.getErrorCode());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Object principal, String authority) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String value = header.substring(7).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, code.getMessage()));
    }
}
