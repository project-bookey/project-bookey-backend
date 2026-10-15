package app.bookey.domain.novel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;

class NovelHomeFeedBatchTest {
    @Test @DisplayName("첫 묶음은 피드 순서에서 받은 열매·내 차례 작품을 빼고 앞에서부터 자리 수만큼이다")
    void firstBatchSkipsPlacedAheadInFeedOrder() {
        List<Long> latestFirst = List.of(9L, 8L, 7L, 6L, 5L, 4L);

        assertThat(NovelHomeFeedBatch.first(latestFirst, Set.of(8L, 6L), 3)).containsExactly(9L, 7L, 5L);
    }

    @Test @DisplayName("첫 묶음은 작품이 모자라면 있는 만큼만 둔다")
    void firstBatchMayBeShort() {
        assertThat(NovelHomeFeedBatch.first(List.of(3L, 2L), Set.of(), 7)).containsExactly(3L, 2L);
    }

    @Test @DisplayName("새로 고른 작품이 자리 수만큼이면 이전 묶음은 남기지 않는다")
    void freshPicksFillEverySlot() {
        List<Long> next = NovelHomeFeedBatch.refreshed(List.of(11L, 12L, 13L), List.of(1L, 2L, 3L), 3, new Random(1));

        assertThat(next).containsExactly(11L, 12L, 13L);
    }

    @Test @DisplayName("새 작품이 모자라면 고른 순서대로 앞에 두고, 남는 자리는 이전 묶음에서 겹치지 않게 채운다")
    void shortPicksAreFilledFromPrevious() {
        List<Long> next = NovelHomeFeedBatch.refreshed(List.of(11L, 2L), List.of(1L, 2L, 3L, 4L), 4, new Random(7));

        assertThat(next).hasSize(4).doesNotHaveDuplicates().startsWith(11L, 2L);
        assertThat(next.subList(2, 4)).isSubsetOf(1L, 3L, 4L);
    }

    @Test @DisplayName("이전 묶음까지 모자라면 있는 만큼만 두고, 어떤 경우에도 자리 수를 넘기지 않는다")
    void neverExceedsSize() {
        assertThat(NovelHomeFeedBatch.refreshed(List.of(11L), List.of(1L), 7, new Random(3))).containsExactly(11L, 1L);
        assertThat(NovelHomeFeedBatch.refreshed(List.of(11L, 12L, 13L), List.of(1L), 2, new Random(3)))
                .containsExactly(11L, 12L);
    }

    @Test @DisplayName("이전 묶음에서 채우는 작품은 무작위로 고른다")
    void fillsFromPreviousAtRandom() {
        List<Long> previous = LongStream.rangeClosed(1, 20).boxed().toList();
        Set<List<Long>> batches = new HashSet<>();
        for (int seed = 0; seed < 20; seed++) {
            batches.add(NovelHomeFeedBatch.refreshed(List.of(100L), previous, 4, new Random(seed)));
        }

        assertThat(batches).hasSizeGreaterThan(1);
    }
}
