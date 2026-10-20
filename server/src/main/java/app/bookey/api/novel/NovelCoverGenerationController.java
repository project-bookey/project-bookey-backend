package app.bookey.api.novel;

import app.bookey.api.auth.WriteBanGuarded;
import app.bookey.api.novel.dto.NovelDtos.NovelCoverView;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/novels/covers")
@RequiredArgsConstructor
public class NovelCoverGenerationController {
    private final NovelCoverGenerationService service;

    @GetMapping("/generation-config")
    @Operation(summary = "AI 표지 사용 가능 여부와 24시간 시도 한도")
    public NovelCoverGenerationService.NovelCoverGenerationConfig config() { return service.config(); }

    @PostMapping("/generate")
    @WriteBanGuarded
    @Operation(summary = "작품 제목·소개·장르로 표지 한 장을 생성하고 임시 저장")
    public NovelCoverView generate(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody NovelCoverGenerationService.NovelCoverGenerationRequest request) {
        return service.generate(user.id(), request);
    }
}
