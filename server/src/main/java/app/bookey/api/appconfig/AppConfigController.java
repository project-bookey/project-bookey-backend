package app.bookey.api.appconfig;

import app.bookey.api.appconfig.dto.AppConfigDtos.AppConfigView;
import app.bookey.domain.user.DevicePlatform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** 앱 시작 시 읽는 설정 — 강제 업데이트 여부와 점검 안내. 로그인 전에도 읽는다. */
@Tag(name = "Public", description = "공개 웹 — 독후감 · 검증 리뷰 (비회원)")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService appConfigService;

    @Operation(summary = "앱 설정 — 플랫폼·버전을 보내면 강제/권장 업데이트 여부와 점검 안내를 돌려준다(1분 캐시)")
    @GetMapping("/app-config")
    public ResponseEntity<AppConfigView> appConfig(@RequestParam DevicePlatform platform,
                                                   @RequestParam(required = false) String version) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(appConfigService.publicConfig(platform, version));
    }
}
