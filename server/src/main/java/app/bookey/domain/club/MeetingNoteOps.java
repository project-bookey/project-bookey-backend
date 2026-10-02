package app.bookey.domain.club;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;

import java.util.*;

/**
 * 모임 공유 노트 연산 — 순수 규칙.
 *
 * <p>연산은 요소 id 기준 두 가지다.
 * <ul>
 *   <li>{@code {"t":"upsert","el":{...}}} — 같은 id 가 있으면 통째로 바꾸고, 없으면 뒤에 붙인다.</li>
 *   <li>{@code {"t":"delete","id":"..."}} — 없으면 아무 일도 없다.</li>
 * </ul>
 * 같은 연산을 두 번 적용해도 결과가 같다(멱등) — 연결이 끊겨 앱이 다시 보내도 안전하다. 같은 요소를 두 멤버가 고치면
 * 나중에 도착한 쪽이 이긴다. 그리는 순서는 요소의 z 가 정하므로 배열 순서는 의미가 없다.
 */
public final class MeetingNoteOps {

    /** 한 번에 보낼 수 있는 연산 수. 앱은 짧은 간격으로 바뀐 요소만 보내므로 정상 사용에선 닿지 않는다. */
    public static final int MAX_OPS = 200;
    /** 요소 상한 — 대형노트는 획 하나가 요소 하나라 독후감 노트보다 넉넉하게 둔다. */
    public static final int MAX_ELEMENTS = 1500;
    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    private static final int MAX_ID_LENGTH = 64;

    /** 빈 노트 — 앱의 대형노트 문서 모양. */
    public static Map<String, Object> emptyDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("v", 1);
        doc.put("paper", "grid");
        doc.put("kind", "large");
        doc.put("elements", List.of());
        return doc;
    }

    private MeetingNoteOps() {
    }

    /** 검증을 마친 연산 하나. upsert 면 element 가, delete 면 null 이다. */
    public record Op(String id, Map<String, Object> element) {
        public boolean isDelete() {
            return element == null;
        }
    }

    /** 요청 본문을 연산 목록으로 좁힌다. 모양이 틀리면 INVALID_REQUEST — 일부만 적용하지 않는다. */
    public static List<Op> parse(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "연산이 비어 있습니다.");
        }
        if (raw.size() > MAX_OPS) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "연산은 한 번에 " + MAX_OPS + "개까지 보낼 수 있습니다.");
        }
        List<Op> ops = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> map)) {
                throw invalid();
            }
            Object t = map.get("t");
            if ("upsert".equals(t) && map.get("el") instanceof Map<?, ?> el) {
                ops.add(new Op(requireId(el.get("id")), copy(el)));
            } else if ("delete".equals(t)) {
                ops.add(new Op(requireId(map.get("id")), null));
            } else {
                throw invalid();
            }
        }
        return ops;
    }

    /** 문서에 연산을 적용한 새 문서. 원본은 건드리지 않는다. 요소 상한을 넘으면 거절한다. */
    public static Map<String, Object> apply(Map<String, Object> document, List<Op> ops) {
        LinkedHashMap<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> element : elementsOf(document)) {
            if (element.get("id") instanceof String id) {
                byId.put(id, element);
            }
        }
        for (Op op : ops) {
            if (op.isDelete()) {
                byId.remove(op.id());
            } else {
                byId.put(op.id(), op.element());
            }
        }
        if (byId.size() > MAX_ELEMENTS) {
            throw new ApiException(ErrorCode.MEETING_NOTE_TOO_LARGE, "요소는 " + MAX_ELEMENTS + "개까지 둘 수 있습니다.");
        }
        Map<String, Object> next = new LinkedHashMap<>(emptyDocument());
        if (document != null) {
            // v·paper·kind 는 앱이 정한 값을 그대로 둔다(빈 노트면 기본값).
            for (String key : List.of("v", "paper", "kind")) {
                if (document.containsKey(key)) {
                    next.put(key, document.get(key));
                }
            }
        }
        next.put("elements", new ArrayList<>(byId.values()));
        return next;
    }

    public static int elementCount(Map<String, Object> document) {
        return elementsOf(document).size();
    }

    /** 요소들이 참조하는 사진 id — 숫자 imageId 만 모은다. */
    public static Set<Long> referencedImageIds(Map<String, Object> document) {
        Set<Long> ids = new HashSet<>();
        for (Map<String, Object> element : elementsOf(document)) {
            if (element.get("imageId") instanceof Number id) {
                ids.add(id.longValue());
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> elementsOf(Map<String, Object> document) {
        if (document == null || !(document.get("elements") instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object e : list) {
            if (e instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private static String requireId(Object id) {
        if (!(id instanceof String s) || s.isEmpty() || s.length() > MAX_ID_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "요소 id 가 올바르지 않습니다.");
        }
        return s;
    }

    private static Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_REQUEST, "연산 형식이 올바르지 않습니다.");
    }
}
