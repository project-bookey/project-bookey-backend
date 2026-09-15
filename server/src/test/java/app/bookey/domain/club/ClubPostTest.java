package app.bookey.domain.club;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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

    @Test
    @DisplayName("고치면 보낸 값으로 바뀌고 고친 시각이 남는다 — 쪽을 바꾸면 가림 기준도 따라간다")
    void editReplacesContentAndStampsTime() {
        ClubPost log = ClubPost.log(1L, 1L, 2L, "여기서 멈췄다", 87, null, 5L, IMAGE);
        Instant editedAt = Instant.parse("2026-09-15T12:00:00Z");

        log.edit("다시 읽어 보니 여기였다", 120, SpoilerLevel.PAGE, editedAt);

        assertThat(log.getBody()).isEqualTo("다시 읽어 보니 여기였다");
        assertThat(log.getAnchorPage()).isEqualTo(120);
        assertThat(log.getEditedAt()).isEqualTo(editedAt);
        assertThat(log.isMaskedFor(87, false, false)).as("기준이 120쪽으로 올라갔다").isTrue();
        assertThat(log.isMaskedFor(120, false, false)).isFalse();
    }

    @Test
    @DisplayName("쪽을 떼면 아무에게도 가려지지 않는다")
    void editCanRemoveThePage() {
        ClubPost log = ClubPost.log(1L, 1L, 2L, "여기서 멈췄다", 87, null, 5L, IMAGE);

        log.edit("쪽은 빼고 싶어요", null, SpoilerLevel.NONE, Instant.parse("2026-09-15T12:00:00Z"));

        assertThat(log.getAnchorPage()).isNull();
        assertThat(log.getSpoilerLevel()).isEqualTo(SpoilerLevel.NONE);
        assertThat(log.isMaskedFor(0, false, false)).isFalse();
    }

    @Test
    @DisplayName("사진만 남기고 한 줄을 지울 수 있다 — 빈 본문도 그대로 반영한다")
    void editCanClearTheBody() {
        ClubPost log = ClubPost.log(1L, 1L, 2L, "지울 한 줄", 87, null, 5L, IMAGE);

        log.edit("", 87, SpoilerLevel.PAGE, Instant.parse("2026-09-15T12:00:00Z"));

        assertThat(log.getBody()).isEmpty();
        assertThat(log.getImageUrl()).as("사진은 그대로").isEqualTo("https://cdn/clubs/1/2/a.jpg");
    }
}
