package app.bookey.api.legal;

import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.domain.legal.LegalDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalDocumentServiceTest {

    private final LegalDocumentService service = new LegalDocumentService();

    @Test
    @DisplayName("모든 문서는 원문 파일이 있고, 공개 문서의 버전은 시행일(yyyy-MM-dd)이며 본문 첫 줄에 같은 시행일을 적는다")
    void everyDocumentHasBody() {
        for (LegalDocument document : LegalDocument.values()) {
            String body = service.body(document);
            assertThat(body).as(document.getKey()).isNotBlank();
            if (document.isPublished()) {
                LocalDate date = LocalDate.parse(document.getVersion());
                assertThat(body).as(document.getKey())
                        .startsWith("시행일: %d년 %d월 %d일".formatted(date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
            }
        }
    }

    @Test
    @DisplayName("공개 문서는 key 로 읽히고, 초안(YES24)·없는 key 는 NOT_FOUND")
    void viewOnlyPublished() {
        var terms = service.view("terms");
        assertThat(terms.title()).isEqualTo(LegalDocument.TERMS.getTitle());
        assertThat(terms.version()).isEqualTo(LegalDocument.TERMS.getVersion());
        assertThat(terms.body()).contains("제1조 (목적)");

        for (String key : new String[]{"third-party-yes24", "nope"}) {
            assertThatThrownBy(() -> service.view(key))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }
    }
}
