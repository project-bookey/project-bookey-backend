package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos;
import app.bookey.api.banner.dto.BannerDtos.BannerView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.support.AfterCommit;
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
    private final BannerImageService imageService;

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
        String previousImage = banner.getImageUrl();
        banner.update(req.title(), req.kind(), req.subtitle(), req.imageUrl(), req.bgColor(),
                req.linkUrl(), req.sortOrder(), req.enabled(), req.startsAt(), req.endsAt());
        if (previousImage != null && !previousImage.equals(req.imageUrl())) {
            releaseImage(previousImage);
        }
        return BannerDtos.BannerAdminView.from(banner);
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.BANNER_NOT_FOUND));
        String image = banner.getImageUrl();
        bannerRepository.delete(banner);
        if (image != null) {
            releaseImage(image);
        }
    }

    /** 이미지를 바꾸거나 배너를 지우면, 다른 배너가 같은 이미지를 쓰지 않을 때만 커밋 뒤에 파일을 지운다. */
    private void releaseImage(String imageUrl) {
        bannerRepository.flush();
        if (!bannerRepository.existsByImageUrl(imageUrl)) {
            AfterCommit.run(() -> imageService.deleteQuietly(imageUrl));
        }
    }
}
