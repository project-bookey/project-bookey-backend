package app.bookey.domain.banner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    List<Banner> findAllByKindAndEnabledTrueOrderBySortOrderAscIdAsc(BannerKind kind);
    List<Banner> findAllByOrderBySortOrderAscIdAsc();
    List<Banner> findAllByKindOrderBySortOrderAscIdAsc(BannerKind kind);

    /** 같은 이미지를 쓰는 다른 배너가 있는지 — 복제한 배너가 같은 파일을 쓸 수 있어 지우기 전에 본다. */
    boolean existsByImageUrl(String imageUrl);
}
