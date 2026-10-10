package app.bookey.api.push;

import app.bookey.domain.push.PushCampaignKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushMessagesTest {

    @Test
    @DisplayName("광고는 제목에 (광고), 본문 끝에 수신 거부 방법을 붙인다 — 이미 붙어 있으면 두 번 붙이지 않는다")
    void marketingLabels() {
        assertThat(PushMessages.title(PushCampaignKind.MARKETING, "가을 이벤트")).isEqualTo("(광고) 가을 이벤트");
        assertThat(PushMessages.title(PushCampaignKind.MARKETING, "(광고) 가을 이벤트")).isEqualTo("(광고) 가을 이벤트");
        assertThat(PushMessages.body(PushCampaignKind.MARKETING, "책갈피 2배")).contains("수신 거부");
    }

    @Test
    @DisplayName("공지는 그대로 보낸다")
    void noticeUnchanged() {
        assertThat(PushMessages.title(PushCampaignKind.NOTICE, " 점검 안내 ")).isEqualTo("점검 안내");
        assertThat(PushMessages.body(PushCampaignKind.NOTICE, "내일 새벽")).isEqualTo("내일 새벽");
    }
}
