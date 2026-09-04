package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos;
import app.bookey.api.banner.dto.BannerDtos.BannerView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.banner.Banner;
import app.bookey.domain.banner.BannerKind;
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
        return activeBanners(BannerKind.AD);
    }

    @Transactional(readOnly = true)
    public List<BannerView> activeBanners(BannerKind kind) {
        return activeAt(bannerRepository.findAllByKindAndEnabledTrueOrderBySortOrderAscIdAsc(kind), Instant.now());
    }

    /** 기간 필터만 담당 — 정렬·enabled 필터는 쿼리가 이미 보장한다. */
    static List<BannerView> activeAt(List<Banner> banners, Instant now) {
        return banners.stream()
                .filter(b -> b.isActiveAt(now))
                .map(BannerView::from)
                .toList();
    }

    /** 어드민 전체 목록 — 비활성·기간 외 배너도 포함한다. */
    @Transactional(readOnly = true)
    public List<BannerDtos.BannerAdminView> adminList() {
        return bannerRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(BannerDtos.BannerAdminView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BannerDtos.BannerAdminView> adminList(BannerKind kind) {
        return bannerRepository.findAllByKindOrderBySortOrderAscIdAsc(kind).stream()
                .map(BannerDtos.BannerAdminView::from).toList();
    }

    @Transactional
    public BannerDtos.BannerAdminView create(BannerDtos.BannerUpsertRequest req) {
        Banner banner = Banner.builder()
                .title(req.title()).kind(req.kind()).subtitle(req.subtitle()).imageUrl(req.imageUrl())
                .bgColor(req.bgColor()).linkUrl(req.linkUrl()).sortOrder(req.sortOrder())
                .enabled(req.enabled()).startsAt(req.startsAt()).endsAt(req.endsAt())
                .build();
        return BannerDtos.BannerAdminView.from(bannerRepository.save(banner));
    }

    @Transactional
    public BannerDtos.BannerAdminView update(Long id, BannerDtos.BannerUpsertRequest req) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.BANNER_NOT_FOUND));
        banner.update(req.title(), req.kind(), req.subtitle(), req.imageUrl(), req.bgColor(),
                req.linkUrl(), req.sortOrder(), req.enabled(), req.startsAt(), req.endsAt());
        return BannerDtos.BannerAdminView.from(banner);
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.BANNER_NOT_FOUND));
        bannerRepository.delete(banner);
    }
}
