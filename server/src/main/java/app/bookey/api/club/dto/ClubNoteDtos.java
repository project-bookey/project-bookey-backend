package app.bookey.api.club.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 모임 노트북 — 함께 꾸미는 페이지와 사진. */
public final class ClubNoteDtos {

    private ClubNoteDtos() {
    }

    /** 앱이 상한을 하드코딩하지 않도록 노트북 조회에 함께 내린다(ClubSeatPolicy 와 같은 이유). */
    public record ClubNotePolicy(int maxPages, int maxElements, int maxDocumentBytes, int maxImageBytes) {}

    /** 페이지를 만들거나 마지막으로 고친 사람. 탈퇴한 사용자는 닉네임이 "알 수 없음" 으로 온다. */
    public record ClubNoteEditorView(@NotNull Long userId, @NotNull String nickname, String avatarUrl) {}

    public record ClubNotePageSummaryView(
            @NotNull Long id,
            int seq,
            String title,
            int version,
            int elementCount,
            ClubNoteEditorView updatedBy,
            @NotNull Instant updatedAt
    ) {}

    /** 끝난 모임은 readOnly — 페이지를 만들·고칠·지울 수 없고 보기만 된다. */
    public record ClubNotebookView(
            @NotNull Long clubId,
            boolean readOnly,
            @NotNull ClubNotePolicy policy,
            List<ClubNotePageSummaryView> pages
    ) {}

    /** document 는 앱이 소유한 JSON — 서버는 해석하지 않고 그대로 돌려준다. */
    public record ClubNotePageView(
            @NotNull Long id,
            int seq,
            String title,
            int version,
            Map<String, Object> document,
            int elementCount,
            ClubNoteEditorView createdBy,
            ClubNoteEditorView updatedBy,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            /** 만든 사람이거나 호스트·운영자면 지울 수 있다. */
            boolean canDelete
    ) {}

    public record CreateClubNotePageRequest(@Size(max = 60) String title) {}

    /**
     * 전체 덮어쓰기. version 이 서버와 다르면 409 CLUB_NOTE_CONFLICT — 앱은 페이지를 다시 GET 해 병합한 뒤 그 version 으로 다시 보낸다.
     * version 은 일부러 래퍼다 — 원시 int 면 빠뜨렸을 때 0 으로 조용히 통과해 새 페이지를 덮어쓴다.
     */
    public record SaveClubNotePageRequest(
            @NotNull Integer version,
            @Size(max = 60) String title,
            @NotNull Map<String, Object> document
    ) {}

    /** 응답 id·url 을 문서의 photo 요소(imageId·url)에 넣고 저장해야 24시간 뒤 정리되지 않는다. */
    public record ClubNoteImageView(@NotNull Long id, @NotNull String url, Integer width, Integer height) {}
}
