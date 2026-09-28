package app.bookey.domain.club;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClubNotePageTest {

    @Test
    @DisplayName("새 페이지는 version 0 · 빈 문서로 시작하고 만든 사람이 마지막 편집자다")
    void startsEmpty() {
        ClubNotePage page = ClubNotePage.create(10L, 3, "첫 모임", 1L);

        assertThat(page.getVersion()).isZero();
        assertThat(page.getDocument()).isEmpty();
        assertThat(page.getElementCount()).isZero();
        assertThat(page.getSeq()).isEqualTo(3);
        assertThat(page.getCreatedBy()).isEqualTo(1L);
        assertThat(page.getUpdatedBy()).isEqualTo(1L);
        assertThat(page.isCreatedBy(1L)).isTrue();
        assertThat(page.isCreatedBy(2L)).isFalse();
        assertThat(page.belongsTo(10L)).isTrue();
        assertThat(page.belongsTo(11L)).isFalse();
    }

    @Test
    @DisplayName("overwrite 는 version 을 1 올리고 문서·요소 수·편집자를 바꾼다; null 문서는 빈 문서로 둔다")
    void overwriteBumpsVersion() {
        ClubNotePage page = ClubNotePage.create(10L, 1, null, 1L);
        Map<String, Object> document = Map.of("v", 1, "elements", java.util.List.of(Map.of("type", "text")));

        page.overwrite("고친 제목", document, 1, 2L);
        assertThat(page.getVersion()).isEqualTo(1);
        assertThat(page.getTitle()).isEqualTo("고친 제목");
        assertThat(page.getDocument()).isSameAs(document);
        assertThat(page.getElementCount()).isEqualTo(1);
        assertThat(page.getUpdatedBy()).isEqualTo(2L);
        assertThat(page.getCreatedBy()).isEqualTo(1L);

        page.overwrite(null, null, 0, 3L);
        assertThat(page.getVersion()).isEqualTo(2);
        assertThat(page.getDocument()).isEmpty();
    }
}
