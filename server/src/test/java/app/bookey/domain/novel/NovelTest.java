package app.bookey.domain.novel;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class NovelTest {
    private static final Instant START = Instant.parse("2026-10-10T00:00:00Z");
    private Novel relay(int chapters) { return new Novel(1L, NovelKind.RELAY, "제목", "소개", "일상", 6, chapters, 24, true, "code"); }
    @Test void soloOnlyOwnerWrites() {
        Novel n = new Novel(1L, NovelKind.SOLO, "제목", "", "일상", 6, 3, 24, true, "code");
        assertThat(n.canWrite(1L)).isTrue(); assertThat(n.canWrite(2L)).isFalse();
        n.publish(List.of(1L), START);
        assertThat(n.getCurrentWriterId()).isEqualTo(1L); assertThat(n.getTurnDueAt()).isNull();
        assertThat(n.getTurnNumber()).isEqualTo(2);
    }
    @Test void relayStartsWithOwnerThenCycles() {
        Novel n = relay(10); n.start(START);
        assertThat(n.getCurrentWriterId()).isEqualTo(1L);
        n.publish(List.of(1L, 2L, 3L), START.plusSeconds(60));
        assertThat(n.getCurrentWriterId()).isEqualTo(2L);
        assertThat(n.getTurnDueAt()).isEqualTo(START.plusSeconds(60).plus(Duration.ofHours(24)));
        n.publish(List.of(1L, 2L, 3L), START); n.publish(List.of(1L, 2L, 3L), START);
        assertThat(n.getCurrentWriterId()).isEqualTo(1L);
    }
    @Test void deadlineIsExclusiveAndSkipsAtBoundary() {
        Novel n = relay(10); n.start(START);
        n.expire(List.of(1L, 2L, 3L), START.plus(Duration.ofHours(24)).minusNanos(1));
        assertThat(n.getCurrentWriterId()).isEqualTo(1L);
        n.expire(List.of(1L, 2L, 3L), START.plus(Duration.ofHours(24)));
        assertThat(n.getCurrentWriterId()).isEqualTo(2L);
        assertThat(n.getChapterCount()).isZero(); assertThat(n.getTurnNumber()).isEqualTo(2);
    }
    @Test void delayedSchedulerSkipsAllElapsedTurnsAndKeepsDeadline() {
        Novel n = relay(10); n.start(START);
        n.expire(List.of(1L, 2L, 3L), START.plus(Duration.ofHours(73)));
        assertThat(n.getCurrentWriterId()).isEqualTo(1L);
        assertThat(n.getTurnNumber()).isEqualTo(4);
        assertThat(n.getTurnDueAt()).isEqualTo(START.plus(Duration.ofHours(96)));
    }
    @Test void lastChapterCompletesWithoutAnotherTurn() {
        Novel n = relay(1); n.start(START); n.publish(List.of(1L, 2L), START);
        assertThat(n.getStatus()).isEqualTo(NovelStatus.COMPLETED);
        assertThat(n.getCurrentWriterId()).isNull(); assertThat(n.getTurnDueAt()).isNull();
        assertThat(n.canWrite(1L)).isFalse();
    }
    @Test void emptyParticipantsCompleteInsteadOfStalling() {
        Novel n = relay(10); n.start(START); n.expire(List.of(), START.plus(Duration.ofDays(1)));
        assertThat(n.getStatus()).isEqualTo(NovelStatus.COMPLETED);
    }
    @Test void leavingWriterAdvancesBeforeRemoval() {
        Novel n = relay(10); n.start(START); n.publish(List.of(1L, 2L, 3L), START);
        n.skip(List.of(1L, 2L, 3L), START);
        assertThat(n.getCurrentWriterId()).isEqualTo(3L);
    }
}
