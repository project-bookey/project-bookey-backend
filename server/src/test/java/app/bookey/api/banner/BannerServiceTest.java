package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerView;
import app.bookey.domain.banner.Banner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BannerServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    private Banner banner(String title, Instant startsAt, Instant endsAt) {
        return Banner.builder()
                .title(title).sortOrder(0).enabled(true)
                .startsAt(startsAt).endsAt(endsAt)
                .build();
    }

    @Test
    @DisplayName("기간 밖 배너는 걸러지고, 입력 순서(정렬)는 유지된다")
    void filtersInactiveKeepsOrder() {
        Banner past = banner("지난", NOW.minusSeconds(200), NOW.minusSeconds(100));
        Banner first = banner("첫째", NOW.minusSeconds(100), NOW.plusSeconds(100));
        Banner second = banner("둘째", NOW.minusSeconds(100), NOW.plusSeconds(100));
        Banner future = banner("예정", NOW.plusSeconds(100), NOW.plusSeconds(200));

        List<BannerView> views = BannerService.activeAt(List.of(past, first, second, future), NOW);

        assertThat(views).extracting(BannerView::title).containsExactly("첫째", "둘째");
    }
}
