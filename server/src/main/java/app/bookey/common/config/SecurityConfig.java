package app.bookey.common.config;

import app.bookey.common.security.JwtAuthenticationFilter;
import app.bookey.common.security.JwtTokenProvider;
import app.bookey.common.security.RestAuthenticationEntryPoint;
import app.bookey.common.security.TokenType;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 서비스 API 와 관리자 API 는 완전히 분리된 필터 체인을 갖는다(§F13).
 *  - /admin/v1/**  : ADMIN_ACCESS 토큰만 허용
 *  - 그 외          : USER_ACCESS 토큰
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
                .securityMatcher("/admin/v1/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(adminCorsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/v1/auth/login", "/admin/v1/auth/totp").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint))
                .addFilterBefore(
                        new JwtAuthenticationFilter(tokenProvider, objectMapper, TokenType.ADMIN_ACCESS),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSocketFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/ws/**")
                .csrf(csrf -> csrf.disable())
                // Expo Go·네이티브 런타임의 Origin은 웹 도메인과 다르다. 핸드셰이크만 열고
                // 실제 사용자는 소켓의 첫 auth 메시지에서 USER_ACCESS JWT로 검증한다.
                .cors(cors -> cors.configurationSource(webSocketCorsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(apiCorsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/docs/**", "/swagger-ui/**", "/openapi.json").permitAll()
                        .requestMatchers(HttpMethod.GET, "/legal/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/google").permitAll()
                        // 실시간 연결 — 핸드셰이크는 열어 두고, 첫 메시지의 토큰으로 핸들러가 인증한다
                        .requestMatchers(HttpMethod.GET, "/ws/**").permitAll()
                        // 공개 웹(SEO)용 읽기 전용 엔드포인트
                        .requestMatchers(HttpMethod.GET, "/api/v1/banners").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/faqs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        // 업로드한 사진(로컬 디스크 저장소일 때만 서빙)
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint))
                .addFilterBefore(
                        new JwtAuthenticationFilter(tokenProvider, objectMapper, TokenType.USER_ACCESS),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource apiCorsSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 공개 웹(www.bookey.site)은 서버에서 호출하지만, 브라우저에서 바로 부를 때를 대비해 열어 둔다.
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*", "https://*.bookey.app", "https://bookey.site", "https://*.bookey.site"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 웹소켓은 Origin 대신 연결 직후 USER_ACCESS JWT로 사용자를 인증한다. */
    private CorsConfigurationSource webSocketCorsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 관리자 웹은 별도 도메인에서만 호출한다. */
    private CorsConfigurationSource adminCorsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3100",
                "https://admin.bookey.app",
                "https://bookey-admin-ubr5x3mthq-du.a.run.app"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
