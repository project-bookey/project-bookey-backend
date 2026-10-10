package app.bookey.admin.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** 감사 로그의 before/after 에 넣을 수 있게 응답 레코드를 맵으로 바꾼다. 긴 글은 잘라 로그가 부풀지 않게 한다. */
@Component
@RequiredArgsConstructor
public class AuditSnapshot {

    private static final int MAX_TEXT = 1000;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public Map<String, Object> of(Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> map = objectMapper.convertValue(value, MAP);
        map.replaceAll((key, v) -> v instanceof String text && text.length() > MAX_TEXT
                ? text.substring(0, MAX_TEXT) + "…"
                : v);
        return map;
    }
}
