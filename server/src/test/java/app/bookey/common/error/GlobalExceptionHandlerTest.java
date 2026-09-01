package app.bookey.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 깨진_요청_본문은_400으로_매핑된다() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "broken body", new MockHttpInputMessage(new ByteArrayInputStream(new byte[0])));

        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }
}
