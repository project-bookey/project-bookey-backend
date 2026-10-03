package app.bookey.domain.faq;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** FAQ 순서 바꾸기 — 어드민이 보낸 전체 id 목록 순서대로 0..n-1 을 매긴다. */
public final class FaqOrdering {

    private FaqOrdering() {
    }

    /**
     * 받은 id 가 지금 있는 FAQ 전체와 정확히 같아야 한다(빠짐·남는 것·중복 없음).
     * 다른 관리자가 그사이 FAQ 를 더하거나 지웠으면 거절해, 낡은 목록으로 순서를 덮어쓰지 않게 한다.
     */
    public static void apply(List<Faq> all, List<Long> ids) {
        Set<Long> requested = new HashSet<>(ids);
        Map<Long, Faq> byId = all.stream().collect(Collectors.toMap(Faq::getId, Function.identity()));
        if (requested.size() != ids.size() || !requested.equals(byId.keySet())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "목록이 바뀌었어요. 새로고침 후 다시 시도해 주세요.");
        }
        for (int order = 0; order < ids.size(); order++) {
            byId.get(ids.get(order)).moveTo(order);
        }
    }
}
