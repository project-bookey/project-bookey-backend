package app.bookey.api.publicapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PublicPagingTest {

    @Test
    @DisplayName("음수 page 는 0 으로, size 는 1~50 으로 묶는다")
    void clampsPageAndSize() {
        assertThat(PublicPaging.of(-3, 10)).isEqualTo(PageRequest.of(0, 10));
        assertThat(PublicPaging.of(2, 0)).isEqualTo(PageRequest.of(2, 1));
        assertThat(PublicPaging.of(0, 999)).isEqualTo(PageRequest.of(0, PublicPaging.MAX_PAGE_SIZE));
        assertThat(PublicPaging.of(1, 20)).isEqualTo(PageRequest.of(1, 20));
    }

    @Test
    @DisplayName("상한을 따로 주면 그 값으로 묶고, 목록 길이도 같은 규칙")
    void customMax() {
        assertThat(PublicPaging.of(0, 100, 20)).isEqualTo(PageRequest.of(0, 20));
        assertThat(PublicPaging.size(0, 50)).isEqualTo(1);
        assertThat(PublicPaging.size(70, 50)).isEqualTo(50);
        assertThat(PublicPaging.size(7, 50)).isEqualTo(7);
    }
}
