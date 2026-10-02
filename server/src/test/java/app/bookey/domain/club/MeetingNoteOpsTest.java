package app.bookey.domain.club;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.club.MeetingNoteOps.Op;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 모임 공유 노트 연산 — 요소 id 기준 upsert·delete, 멱등, 상한. */
class MeetingNoteOpsTest {

    private static Map<String, Object> el(String id, Object x) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("type", "text");
        e.put("x", x);
        return e;
    }

    private static Map<String, Object> upsert(Map<String, Object> el) {
        return Map.of("t", "upsert", "el", el);
    }

    private static Map<String, Object> delete(String id) {
        return Map.of("t", "delete", "id", id);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> elements(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("elements");
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("빈 문서에 upsert 하면 대형노트 모양 문서에 요소가 붙는다")
    void upsertIntoEmpty() {
        Map<String, Object> doc = MeetingNoteOps.apply(null, MeetingNoteOps.parse(List.of(upsert(el("a", 1)))));

        assertThat(doc.get("kind")).isEqualTo("large");
        assertThat(doc.get("paper")).isEqualTo("grid");
        assertThat(elements(doc)).extracting(e -> e.get("id")).containsExactly("a");
    }

    @Test
    @DisplayName("같은 id 로 upsert 하면 통째로 바꾸고, 나중에 온 연산이 이긴다")
    void upsertReplacesLastWriterWins() {
        Map<String, Object> doc = MeetingNoteOps.apply(null, MeetingNoteOps.parse(List.of(upsert(el("a", 1)))));
        doc = MeetingNoteOps.apply(doc, MeetingNoteOps.parse(List.of(upsert(el("a", 2)), upsert(el("a", 3)))));

        assertThat(elements(doc)).hasSize(1);
        assertThat(elements(doc).get(0).get("x")).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 연산을 두 번 적용해도 결과가 같다 — 다시 보내도 안전")
    void idempotent() {
        List<Op> ops = MeetingNoteOps.parse(List.of(upsert(el("a", 1)), delete("b")));
        Map<String, Object> once = MeetingNoteOps.apply(MeetingNoteOps.emptyDocument(), ops);
        Map<String, Object> twice = MeetingNoteOps.apply(once, ops);

        assertThat(twice).isEqualTo(once);
    }

    @Test
    @DisplayName("delete 는 요소를 지우고, 없는 id 는 조용히 넘긴다")
    void deleteRemoves() {
        Map<String, Object> doc = MeetingNoteOps.apply(null,
                MeetingNoteOps.parse(List.of(upsert(el("a", 1)), upsert(el("b", 2)))));
        doc = MeetingNoteOps.apply(doc, MeetingNoteOps.parse(List.of(delete("a"), delete("zzz"))));

        assertThat(elements(doc)).extracting(e -> e.get("id")).containsExactly("b");
    }

    @Test
    @DisplayName("원본 문서는 바뀌지 않는다")
    void doesNotMutateOriginal() {
        Map<String, Object> doc = MeetingNoteOps.apply(null, MeetingNoteOps.parse(List.of(upsert(el("a", 1)))));
        MeetingNoteOps.apply(doc, MeetingNoteOps.parse(List.of(delete("a"))));

        assertThat(elements(doc)).hasSize(1);
    }

    @Test
    @DisplayName("문서의 v·paper·kind 는 그대로 둔다")
    void keepsDocumentHeader() {
        Map<String, Object> doc = new HashMap<>(Map.of("v", 2, "paper", "plain", "kind", "large", "elements", List.of()));
        Map<String, Object> next = MeetingNoteOps.apply(doc, MeetingNoteOps.parse(List.of(upsert(el("a", 1)))));

        assertThat(next.get("v")).isEqualTo(2);
        assertThat(next.get("paper")).isEqualTo("plain");
    }

    @Test
    @DisplayName("빈 연산·모르는 연산·id 없는 요소·너무 긴 id 는 거절한다")
    void rejectsMalformed() {
        assertCode(() -> MeetingNoteOps.parse(List.of()), ErrorCode.INVALID_REQUEST);
        assertCode(() -> MeetingNoteOps.parse(null), ErrorCode.INVALID_REQUEST);
        assertCode(() -> MeetingNoteOps.parse(List.of(Map.of("t", "move", "id", "a"))), ErrorCode.INVALID_REQUEST);
        assertCode(() -> MeetingNoteOps.parse(List.of(Map.of("t", "upsert", "el", Map.of("x", 1)))), ErrorCode.INVALID_REQUEST);
        assertCode(() -> MeetingNoteOps.parse(List.of(delete("a".repeat(65)))), ErrorCode.INVALID_REQUEST);
        assertCode(() -> MeetingNoteOps.parse(List.of("upsert")), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("한 번에 보내는 연산은 200개까지")
    void rejectsTooManyOps() {
        List<Object> ops = new ArrayList<>();
        for (int i = 0; i <= MeetingNoteOps.MAX_OPS; i++) {
            ops.add(delete("e" + i));
        }
        assertCode(() -> MeetingNoteOps.parse(ops), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("요소 상한을 넘기면 MEETING_NOTE_TOO_LARGE")
    void rejectsTooManyElements() {
        List<Object> elements = new ArrayList<>();
        for (int i = 0; i < MeetingNoteOps.MAX_ELEMENTS; i++) {
            elements.add(el("e" + i, i));
        }
        Map<String, Object> full = Map.of("v", 1, "paper", "grid", "kind", "large", "elements", elements);

        assertCode(() -> MeetingNoteOps.apply(full, MeetingNoteOps.parse(List.of(upsert(el("new", 0))))),
                ErrorCode.MEETING_NOTE_TOO_LARGE);
        // 이미 있는 요소를 고치는 건 상한과 무관하다
        assertThat(MeetingNoteOps.elementCount(
                MeetingNoteOps.apply(full, MeetingNoteOps.parse(List.of(upsert(el("e0", 9))))))).isEqualTo(MeetingNoteOps.MAX_ELEMENTS);
    }

    @Test
    @DisplayName("사진 참조는 숫자 imageId 만 모은다")
    void referencedImageIds() {
        Map<String, Object> photo = new LinkedHashMap<>(el("p", 0));
        photo.put("imageId", 7);
        Map<String, Object> bogus = new LinkedHashMap<>(el("q", 0));
        bogus.put("imageId", "8");
        Map<String, Object> doc = MeetingNoteOps.apply(null, MeetingNoteOps.parse(List.of(upsert(photo), upsert(bogus))));

        assertThat(MeetingNoteOps.referencedImageIds(doc)).containsExactly(7L);
    }
}
