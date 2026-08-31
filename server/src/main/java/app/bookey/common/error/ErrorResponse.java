package app.bookey.common.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldError> errors,
        Instant timestamp
) {
    public record FieldError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, null, Instant.now());
    }

    public static ErrorResponse of(ErrorCode code, String message, List<FieldError> errors) {
        return new ErrorResponse(code.name(), message, errors, Instant.now());
    }
}
