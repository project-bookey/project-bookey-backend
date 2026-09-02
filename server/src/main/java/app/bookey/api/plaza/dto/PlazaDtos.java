package app.bookey.api.plaza.dto;

import app.bookey.api.plaza.PlazaItemType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class PlazaDtos {

    private PlazaDtos() {}

    /** 광장 피드 아이템 — QUOTE 전용 필드는 FINISH 아이템에서 전부 null이다. */
    public record PlazaItemView(
            @NotNull PlazaItemType type,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            @NotNull Long bookId, @NotNull String bookTitle, String bookCoverUrl,
            @NotNull Instant occurredAt,
            Long quoteId, String content, Integer page, Long agreeCount, Boolean agreedByMe,
            /** 작성자가 이 책을 완독했는가 — 밑줄 카드의 완독 인증 마크. FINISH 아이템은 정의상 true. */
            Boolean authorFinished) {}
}
