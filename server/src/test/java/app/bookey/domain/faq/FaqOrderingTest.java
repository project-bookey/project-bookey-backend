package app.bookey.domain.faq;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.inquiry.InquiryCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaqOrderingTest {

    private static Faq faq(long id, int sortOrder) {
        Faq faq = Faq.builder().category(InquiryCategory.USAGE).question("Q" + id).answer("A" + id)
                .sortOrder(sortOrder).visible(true).build();
        try {
            Field f = Faq.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(faq, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return faq;
    }

    @Test
    @DisplayName("받은 id 순서대로 0부터 순서를 매긴다")
    void appliesOrder() {
        Faq a = faq(1L, 0), b = faq(2L, 1), c = faq(3L, 2);

        FaqOrdering.apply(List.of(a, b, c), List.of(3L, 1L, 2L));

        assertThat(c.getSortOrder()).isZero();
        assertThat(a.getSortOrder()).isEqualTo(1);
        assertThat(b.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("빠진 id·남는 id·중복 id 는 거절한다 — 낡은 목록으로 순서를 덮어쓰지 않게")
    void rejectsMismatchedIds() {
        List<Faq> all = List.of(faq(1L, 0), faq(2L, 1));

        assertThatThrownBy(() -> FaqOrdering.apply(all, List.of(1L)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> FaqOrdering.apply(all, List.of(1L, 2L, 3L)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> FaqOrdering.apply(all, List.of(1L, 1L)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
