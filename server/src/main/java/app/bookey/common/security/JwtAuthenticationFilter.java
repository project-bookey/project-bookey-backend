package app.bookey.common.security;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.error.ErrorResponse;
import app.bookey.domain.admin.Admin;
import app.bookey.domain.admin.AdminRepository;
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
import java.util.Date;
import java.util.List;

/**
 * Bearer 토큰을 검증해 SecurityContext 를 채운다.
 * 서비스용/관리자용 필터를 같은 클래스로 쓰되 기대 토큰 타입이 다르므로,
 * 서비스 JWT 로는 /admin/v1/** 에 절대 접근할 수 없다(§F13 보안 요구사항).
 * <p>
 * 관리자 토큰은 매 요청마다 계정을 다시 읽어 정지 여부와 현재 권한을 반영한다 — 토큰에 박힌 권한을 믿으면
 * 권한을 내리거나 계정을 정지해도 토큰이 끝날 때(30분)까지 그대로 쓸 수 있다.
 * 사용자 토큰은 정지·탈퇴로 폐기된 시각 이전에 발급됐으면 거절한다({@link UserAccessRevocations}).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final TokenType expectedType;
    private final UserAccessRevocations revocations;
    private final AdminRepository adminRepository;

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
                Admin admin = adminRepository.findById(id)
                        .filter(Admin::isActive)
                        .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_TOKEN));
                AuthAdmin principal = new AuthAdmin(admin.getId(), admin.getEmail(), admin.getRole());
                setAuthentication(principal, "ROLE_ADMIN_" + admin.getRole().name());
            } else {
                Date issuedAt = claims.getIssuedAt();
                if (issuedAt != null && revocations.isRevoked(id, issuedAt.toInstant())) {
                    throw ApiException.of(ErrorCode.INVALID_TOKEN);
                }
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
