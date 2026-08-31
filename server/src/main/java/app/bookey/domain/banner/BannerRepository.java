package app.bookey.domain.banner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    List<Banner> findAllByOrderBySortOrderAscIdAsc();
}
