package app.bookey.domain.club;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 읽기로그 조각의 기본값과 스포일러 가림. */
class ClubPostTest {

    private static final ClubPost.LogImage IMAGE =
            new ClubPost.LogImage("https://cdn/clubs/1/2/a.jpg", "clubs/1/2/a.jpg", 1200, 1600);

    @Test
    @DisplayName("쪽에 붙인 조각은 PAGE 가림 — 그 쪽 전까지 읽은 멤버에게는 가려진다")
    void logAnchoredToPageIsMaskedBeforeThatPage() {
        ClubPost log = ClubPost.log(1L, 1L, 2L, "여기서 멈췄다", 87, null, 5L, IMAGE);

        assertThat(log.getType()).isEqualTo(ClubPostType.LOG);
        assertThat(log.getSpoilerLevel()).isEqualTo(SpoilerLevel.PAGE);
        assertThat(log.isMaskedFor(86, false, false)).isTrue();
        assertThat(log.isMaskedFor(87, false, false)).isFalse();
        assertThat(log.isMaskedFor(10, true, false)).as("완독한 멤버").isFalse();
        assertThat(log.isMaskedFor(10, false, true)).as("작성자").isFalse();
    }

    @Test
    @DisplayName("쪽 없이 남긴 조각은 누구에게나 보이고, 사진·세션 정보를 담는다")
    void logWithoutPageIsVisibleToAll() {
        ClubPost log = ClubPost.log(1L, 1L, 2L, null, null, null, 5L, IMAGE);

        assertThat(log.getSpoilerLevel()).isEqualTo(SpoilerLevel.NONE);
        assertThat(log.isMaskedFor(0, false, false)).isFalse();
        assertThat(log.getBody()).isEmpty();
        assertThat(log.getImageUrl()).isEqualTo("https://cdn/clubs/1/2/a.jpg");
        assertThat(log.getImageStorageKey()).isEqualTo("clubs/1/2/a.jpg");
        assertThat(log.getImageWidth()).isEqualTo(1200);
        assertThat(log.getReadingSessionId()).isEqualTo(5L);
    }
}
