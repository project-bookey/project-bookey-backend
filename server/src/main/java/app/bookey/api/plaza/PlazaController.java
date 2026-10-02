package app.bookey.api.plaza;

import app.bookey.api.plaza.dto.PlazaDtos.PlazaItemView;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    @Operation(summary = "광장 피드 — 완독 자랑(FINISH). 밑줄(QUOTE)은 걷어내 늘 빈 페이지")
    @GetMapping("/feed")
    public PageResponse<PlazaItemView> feed(@RequestParam(defaultValue = "FINISH") PlazaItemType type,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return plazaService.feed(type, PageRequest.of(page, size));
    }
}
