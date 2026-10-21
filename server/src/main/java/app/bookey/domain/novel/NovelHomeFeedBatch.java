package app.bookey.domain.novel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 홈 열매 밭의 피드 자리에 놓을 열매(회차) 묶음을 정한다.
 * 어떤 회차가 후보인지는 DB 가 고르고, 여기서는 순서와 개수만 맞춘다.
 */
public final class NovelHomeFeedBatch {
    private NovelHomeFeedBatch() {}

    /**
     * 새로고침한 묶음 — 새로 고른 열매를 고른 순서대로 앞에 두고,
     * 모자란 자리는 이전 묶음에서 무작위로 채워 열매 밭이 비지 않게 한다.
     */
    public static List<Long> refreshed(List<Long> picked, List<Long> previous, int size, RandomGenerator random) {
        List<Long> batch = new ArrayList<>(picked.stream().distinct().limit(size).toList());
        List<Long> leftovers = new ArrayList<>(previous.stream()
                .filter(id -> !batch.contains(id))
                .distinct()
                .toList());
        Collections.shuffle(leftovers, random);
        int emptySlots = size - batch.size();
        batch.addAll(leftovers.subList(0, Math.min(emptySlots, leftovers.size())));
        return List.copyOf(batch);
    }
}
