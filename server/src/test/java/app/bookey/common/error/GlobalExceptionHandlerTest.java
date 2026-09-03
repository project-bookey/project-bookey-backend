package app.bookey.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    @Test
    void 업로드_크기_초과는_413_IMAGE_TOO_LARGE_로_매핑된다() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo("IMAGE_TOO_LARGE");
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE.getMessage());
    }

    @Test
    void 그_밖의_multipart_해석_실패는_400_INVALID_REQUEST_로_매핑된다() {
        ResponseEntity<ErrorResponse> broken = handler.handleMultipart(new MultipartException("boundary 깨짐"));
        ResponseEntity<ErrorResponse> missing = handler.handleMultipart(new MissingServletRequestPartException("file"));

        assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(broken.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(broken.getBody().message()).isEqualTo("업로드 형식이 올바르지 않습니다.");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().code()).isEqualTo("INVALID_REQUEST");
    }
}
