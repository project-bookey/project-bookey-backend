package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Banner", description = "홈 이벤트 배너")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "활성 배너 목록 — 기간 내, 정렬 순")
    @GetMapping
    public List<BannerView> list() {
        return bannerService.activeBanners();
    }
}
