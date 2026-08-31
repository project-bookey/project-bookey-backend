package app.bookey.api.auth;

import app.bookey.api.auth.dto.AuthDtos.*;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "소셜 로그인 · 토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "소셜 로그인 (Apple/Google/Kakao)")
    @PostMapping("/social")
    public TokenResponse socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.socialLogin(request);
    }

    @Operation(summary = "액세스 토큰 재발급")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "로그아웃 — 모든 리프레시 토큰 폐기")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser user) {
        if (user != null) {
            authService.logout(user.id());
        }
        return ResponseEntity.noContent().build();
    }
}
