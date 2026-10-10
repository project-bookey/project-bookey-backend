package app.bookey.api.auth;

import app.bookey.api.auth.dto.AuthDtos.KakaoTokenRequest;
import app.bookey.api.auth.dto.AuthDtos.KakaoTokenResponse;
import app.bookey.common.support.ClientKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** 카카오 로그인 중계 — 흐름은 KakaoLoginService 참고. authorize·callback 은 브라우저가 따라가는 302 다. */
@Tag(name = "Auth", description = "이메일·소셜 로그인 · 토큰")
@RestController
@RequestMapping("/api/v1/auth/kakao")
@RequiredArgsConstructor
public class KakaoLoginController {

    private final KakaoLoginService kakaoLoginService;

    @Operation(summary = "카카오 로그인 시작 — 앱이 연 로그인 창을 카카오 로그인 화면으로 보낸다 (302)")
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("redirect_uri") String redirectUri,
                                          @RequestParam String state,
                                          @RequestParam("code_challenge") String codeChallenge,
                                          @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
                                          HttpServletRequest request) {
        return found(kakaoLoginService.authorize(redirectUri, state, codeChallenge, codeChallengeMethod,
                ClientKeys.ipKey(request)));
    }

    @Operation(summary = "카카오 로그인 콜백 — 카카오 콘솔에 등록하는 리다이렉트 URI. 코드를 토큰으로 바꿔 앱으로 돌려보낸다 (302)")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        return found(kakaoLoginService.callback(code, state, error));
    }

    @Operation(summary = "카카오 교환 코드 → 카카오 액세스 토큰 — 받은 토큰으로 /auth/social(KAKAO)을 부른다")
    @PostMapping("/token")
    public KakaoTokenResponse token(@Valid @RequestBody KakaoTokenRequest request) {
        return new KakaoTokenResponse(kakaoLoginService.exchange(request.code(), request.codeVerifier()));
    }

    private static ResponseEntity<Void> found(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
