package app.bookey.domain.inquiry;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryTest {

    private static final Instant T1 = Instant.parse("2026-10-03T01:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-03T02:00:00Z");

    private static Inquiry inquiry() {
        return Inquiry.builder().userId(7L).category(InquiryCategory.BUG).body("앱이 꺼져요").build();
    }

    @Test
    @DisplayName("새 문의는 답변 대기이고 답변이 비어 있다")
    void newInquiryIsWaiting() {
        Inquiry inquiry = inquiry();

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.WAITING);
        assertThat(inquiry.getAnswer()).isNull();
        assertThat(inquiry.getAnsweredAt()).isNull();
    }

    @Test
    @DisplayName("첫 답변은 답변 완료로 바꾸고 답변자·시각을 채운다")
    void answerMarksAnswered() {
        Inquiry inquiry = inquiry();

        inquiry.answer(3L, "확인했어요", T1);

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(inquiry.getAnswer()).isEqualTo("확인했어요");
        assertThat(inquiry.getAnsweredBy()).isEqualTo(3L);
        assertThat(inquiry.getAnsweredAt()).isEqualTo(T1);
        assertThat(inquiry.getAnswerUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("이미 답한 문의에 다시 등록하면 INQUIRY_ALREADY_ANSWERED — 덮어쓰지 않는다")
    void answerTwiceIsRejected() {
        Inquiry inquiry = inquiry();
        inquiry.answer(3L, "첫 답변", T1);

        assertThatThrownBy(() -> inquiry.answer(4L, "두 번째", T2))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        assertThat(inquiry.getAnswer()).isEqualTo("첫 답변");
        assertThat(inquiry.getAnsweredBy()).isEqualTo(3L);
    }

    @Test
    @DisplayName("답변 수정은 본문·답변자·고친 시각만 바꾸고 첫 답변 시각은 그대로 둔다")
    void editKeepsAnsweredAt() {
        Inquiry inquiry = inquiry();
        inquiry.answer(3L, "첫 답변", T1);

        inquiry.editAnswer(4L, "고친 답변", T2);

        assertThat(inquiry.getAnswer()).isEqualTo("고친 답변");
        assertThat(inquiry.getAnsweredBy()).isEqualTo(4L);
        assertThat(inquiry.getAnsweredAt()).isEqualTo(T1);
        assertThat(inquiry.getAnswerUpdatedAt()).isEqualTo(T2);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
    }

    @Test
    @DisplayName("답변 대기 중인 문의는 고칠 수 없다 — INQUIRY_NOT_ANSWERED")
    void editWaitingIsRejected() {
        Inquiry inquiry = inquiry();

        assertThatThrownBy(() -> inquiry.editAnswer(3L, "고친 답변", T1))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_NOT_ANSWERED);
        assertThat(inquiry.getAnswer()).isNull();
    }

    @Test
    @DisplayName("작성자만 자기 문의의 주인이다")
    void ownership() {
        Inquiry inquiry = inquiry();

        assertThat(inquiry.isOwnedBy(7L)).isTrue();
        assertThat(inquiry.isOwnedBy(8L)).isFalse();
    }
}
