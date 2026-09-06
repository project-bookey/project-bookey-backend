package app.bookey.api.book.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** YES24 응답 정규화 — 저자 문자열("X 저/Y 공역")·출간일(yyyyMMdd)·카테고리 파싱. */
class Yes24ClientTest {

    @Test
    @DisplayName("저자 파싱 — '저' 표기 부분만 저자, '역' 표기 부분은 역자로 가른다")
    void parseAuthorAndTranslator() {
        String raw = "로버트 C. 마틴 저/박재호,이해영 공역";
        assertThat(Yes24Client.parseAuthor(raw)).isEqualTo("로버트 C. 마틴");
        assertThat(Yes24Client.parseTranslator(raw)).isEqualTo("박재호,이해영");

        assertThat(Yes24Client.parseAuthor("루키우스 안나이우스 세네카 저/하와이 대저택 편역"))
                .isEqualTo("루키우스 안나이우스 세네카");
        assertThat(Yes24Client.parseTranslator("루키우스 안나이우스 세네카 저/하와이 대저택 편역"))
                .isEqualTo("하와이 대저택");

        // 구분자가 없으면 원문 그대로, 역자는 없음
        assertThat(Yes24Client.parseAuthor("손원평")).isEqualTo("손원평");
        assertThat(Yes24Client.parseTranslator("손원평")).isNull();
        assertThat(Yes24Client.parseAuthor(null)).isNull();
    }

    @Test
    @DisplayName("출간일 파싱 — yyyyMMdd, 형식이 어긋나면 null")
    void parsePublishDate() {
        assertThat(Yes24Client.parsePublishDate("20131224")).isEqualTo(LocalDate.of(2013, 12, 24));
        assertThat(Yes24Client.parsePublishDate("2013-12")).isNull();
        assertThat(Yes24Client.parsePublishDate("")).isNull();
        assertThat(Yes24Client.parsePublishDate("99999999")).isNull();
    }

    @Test
    @DisplayName("카테고리 파싱 — '국내도서-IT 모바일' → '국내도서 > IT 모바일'")
    void parseCategory() {
        assertThat(Yes24Client.parseCategory("국내도서-IT 모바일")).isEqualTo("국내도서 > IT 모바일");
        assertThat(Yes24Client.parseCategory("")).isNull();
        assertThat(Yes24Client.parseCategory(null)).isNull();
    }
}
