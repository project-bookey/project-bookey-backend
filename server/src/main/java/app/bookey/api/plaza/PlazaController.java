package app.bookey.api.plaza;

import app.bookey.api.plaza.dto.PlazaDtos.PlazaItemView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Plaza", description = "광장 — 전체 사용자 피드")
@RestController
@RequestMapping("/api/v1/plaza")
@RequiredArgsConstructor
public class PlazaController {

    private final PlazaService plazaService;

    @Operation(summary = "광장 피드 — 밑줄(QUOTE) · 완독 자랑(FINISH)")
    @GetMapping("/feed")
    public PageResponse<PlazaItemView> feed(@AuthenticationPrincipal AuthUser user,
                                            @RequestParam(defaultValue = "QUOTE") PlazaItemType type,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return plazaService.feed(user.id(), type, PageRequest.of(page, size));
    }
}
