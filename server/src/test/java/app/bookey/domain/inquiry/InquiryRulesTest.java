package app.bookey.domain.inquiry;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryRulesTest {

    private static InquiryImage image(long id, long userId, Long inquiryId) {
        InquiryImage image = InquiryImage.builder()
                .userId(userId).storageKey("k" + id).url("http://x/k" + id).contentType("image/png").byteSize(1)
                .build();
        set(image, "id", id);
        if (inquiryId != null) {
            set(image, "inquiryId", inquiryId);
        }
        return image;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("사진 id 는 null 을 빼고 중복은 첫 것만 남긴다")
    void normalizeImageIds() {
        assertThat(InquiryRules.normalizeImageIds(null)).isEmpty();
        assertThat(InquiryRules.normalizeImageIds(Arrays.asList(3L, null, 1L, 3L))).containsExactly(3L, 1L);
    }

    @Test
    @DisplayName("사진이 3장을 넘으면 INVALID_REQUEST")
    void tooManyImages() {
        assertThatThrownBy(() -> InquiryRules.normalizeImageIds(List.of(1L, 2L, 3L, 4L)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("답변 대기 문의가 5건이면 새 문의를 받지 않는다")
    void pendingLimit() {
        assertThatCode(() -> InquiryRules.requirePendingBelowLimit(4)).doesNotThrowAnyException();
        assertThatThrownBy(() -> InquiryRules.requirePendingBelowLimit(5))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_PENDING_LIMIT);
    }

    @Test
    @DisplayName("내 것이고 아직 붙지 않은 사진만 붙일 수 있다")
    void validAttachments() {
        assertThatCode(() -> InquiryRules.validateAttachments(10L, List.of(1L, 2L),
                List.of(image(1L, 10L, null), image(2L, 10L, null)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("없는 사진·남의 사진·이미 붙은 사진은 모두 INQUIRY_IMAGE_NOT_FOUND")
    void invalidAttachments() {
        assertThatThrownBy(() -> InquiryRules.validateAttachments(10L, List.of(1L, 2L), List.of(image(1L, 10L, null))))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
        assertThatThrownBy(() -> InquiryRules.validateAttachments(10L, List.of(1L), List.of(image(1L, 99L, null))))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
        assertThatThrownBy(() -> InquiryRules.validateAttachments(10L, List.of(1L), List.of(image(1L, 10L, 5L))))
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("미리보기는 줄바꿈·연속 공백을 한 칸으로 접는다")
    void previewFlattensWhitespace() {
        assertThat(InquiryRules.preview("  첫 줄\n\n둘째   줄\t끝  ")).isEqualTo("첫 줄 둘째 줄 끝");
    }

    @Test
    @DisplayName("미리보기는 80자에서 자르고 말줄임을 붙인다 — 이모지를 반으로 가르지 않는다")
    void previewCutsByCodePoint() {
        String body = "가".repeat(79) + "😀😀😀";

        String preview = InquiryRules.preview(body);

        assertThat(preview).isEqualTo("가".repeat(79) + "😀…");
        assertThat(InquiryRules.preview("가".repeat(80))).isEqualTo("가".repeat(80));
    }

    @Test
    @DisplayName("어드민 정렬 — 답변 대기는 오래된 순, 그 밖에는 최신순")
    void adminSort() {
        assertThat(InquiryRules.adminSort(InquiryStatus.WAITING).getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(InquiryRules.adminSort(InquiryStatus.ANSWERED).getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(InquiryRules.adminSort(null).getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("기기 정보는 거절하지 않고 칸 길이에 맞춰 자르며, 빈 값은 null 로 둔다")
    void clipDeviceInfo() {
        assertThat(InquiryRules.clip("  iPhone 15  ", 100)).isEqualTo("iPhone 15");
        assertThat(InquiryRules.clip("abcdef", 3)).isEqualTo("abc");
        assertThat(InquiryRules.clip("   ", 10)).isNull();
        assertThat(InquiryRules.clip(null, 10)).isNull();
    }
}
