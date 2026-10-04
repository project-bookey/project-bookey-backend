package app.bookey.api.plaza.dto;

import app.bookey.api.plaza.PlazaItemType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class PlazaDtos {

    private PlazaDtos() {}

    /**
     * 광장 피드 아이템 — 완독 자랑. occurredAt 은 완독한 시각이다.
     * remark 는 그 회차(읽기 기록)를 덮으며 남긴 완독 한 마디 — 남기지 않았으면 없다.
     */
    public record PlazaItemView(
            @NotNull PlazaItemType type,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            @NotNull Long bookId, @NotNull String bookTitle, String bookCoverUrl,
            @NotNull Instant occurredAt,
            String remark) {}
}
