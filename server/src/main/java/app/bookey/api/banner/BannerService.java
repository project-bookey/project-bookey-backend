package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerView;
import app.bookey.domain.banner.Banner;
import app.bookey.domain.banner.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository bannerRepository;

    @Transactional(readOnly = true)
    public List<BannerView> activeBanners() {
        return activeAt(bannerRepository.findAllByEnabledTrueOrderBySortOrderAscIdAsc(), Instant.now());
    }

    /** 기간 필터만 담당 — 정렬·enabled 필터는 쿼리가 이미 보장한다. */
    static List<BannerView> activeAt(List<Banner> banners, Instant now) {
        return banners.stream()
                .filter(b -> b.isActiveAt(now))
                .map(BannerView::from)
                .toList();
    }
}
