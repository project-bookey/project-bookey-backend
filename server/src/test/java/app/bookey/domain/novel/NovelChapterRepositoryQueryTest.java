package app.bookey.domain.novel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class NovelChapterRepositoryQueryTest {
    // 텍스트 블록은 줄 끝 공백을 지운다. 'WHERE """ + 조건' 처럼 이으면 WHERE 와 조건이 붙어(WHEREc.state) 쿼리가 깨지는데,
    // 서비스 테스트는 저장소를 흉내 내서 운영 DB 에서야 드러난다.
    @Test @DisplayName("이어 붙인 네이티브 쿼리에서 WHERE·AND 가 다음 조건과 붙지 않는다")
    void nativeQueriesKeepKeywordsApartFromConditions() {
        List<String> nativeQueries = Stream.of(NovelChapterRepository.class, NovelRepository.class)
                .flatMap(repository -> Arrays.stream(repository.getDeclaredMethods()))
                .map(method -> method.getAnnotation(Query.class))
                .filter(query -> query != null && query.nativeQuery())
                .map(Query::value)
                .toList();

        assertThat(nativeQueries).isNotEmpty()
                .allSatisfy(sql -> assertThat(sql).doesNotContainPattern("\\b(WHERE|AND|OR)[a-z]"));
    }
}
