package app.bookey.api.remark.dto;

import app.bookey.domain.remark.BookRemark;
import app.bookey.domain.remark.RemarkKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class RemarkDtos {

    private RemarkDtos() {}

    public record RemarkRequest(@NotBlank @Size(max = BookRemark.MAX_LENGTH) String body) {}

    /** 한 마디 한 줄 — 누가, 다 읽고(완독) 남겼는지 내려놓으며(하차) 남겼는지. */
    public record RemarkView(
            @NotNull Long id,
            @NotNull Long bookId,
            @NotNull Long authorId,
            @NotNull String authorNickname,
            @NotNull RemarkKind kind,
            @NotNull String body,
            @NotNull Instant writtenAt
    ) {}
}
