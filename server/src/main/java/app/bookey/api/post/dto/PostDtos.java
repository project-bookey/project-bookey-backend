package app.bookey.api.post.dto;

import app.bookey.domain.post.PostVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

public final class PostDtos {

    private PostDtos() {}

    /** 피드 정렬 (§14.1) — HOT: 좋아요·시간 감쇠 점수, NEW: 최신순. */
    public enum FeedSort { HOT, NEW }

    /**
     * bodyMd 는 비면 안 된다. clubId 를 주면 모임 독후감 — 공개 범위는 PUBLIC·CLUB 만. 사진은 10장까지.
     * 노트 형식(format·document)은 걷어냈다 — 옛 앱이 보내도 모르는 필드라 무시된다.
     * quoteIds 는 밑줄 기능을 걷어내 쓰지 않는다 — 옛 앱이 보내도 받기만 하고 무시한다.
     */
    public record CreatePostRequest(Long bookId, Long readingRecordId,
            @NotBlank @Size(max = 300) String title, @NotNull @Size(max = 20000) String bodyMd,
            @NotNull PostVisibility visibility, List<@Size(max = 30) String> tags,
            @Size(max = 10, message = "사진은 {max}장까지 붙일 수 있어요.") List<Long> imageIds,
            @Schema(requiredMode = NOT_REQUIRED, description = "더 쓰지 않는다 — 보내도 무시한다(옛 앱 호환)")
            List<Long> quoteIds,
            @Schema(requiredMode = NOT_REQUIRED, description = "모임 안에서 쓸 때 그 모임 id — 활성 멤버·진행 중 모임만")
            Long clubId) {}

    /**
     * null = 유지, 빈 리스트 = 비움. bookId 는 바꿀 수만 있고 없앨 수는 없다.
     * 컬렉션 셋은 생략에 뜻이 있으므로 {@code NOT_REQUIRED} 로 문서에서도 선택 항목으로 내보낸다
     * — 생성 타입이 필수로 나가면 클라이언트가 {@code []} 를 채워 보내 첨부가 전부 지워진다.
     * clubId 는 바꿀 수 없어 받지 않는다.
     */
    public record UpdatePostRequest(Long bookId, @Size(max = 300) String title, @Size(max = 20000) String bodyMd,
            @Schema(requiredMode = NOT_REQUIRED, description = "생략하면 유지, 빈 목록이면 비움")
            List<@Size(max = 30) String> tags,
            PostVisibility visibility,
            @Schema(requiredMode = NOT_REQUIRED, description = "생략하면 유지, 빈 목록이면 사진을 전부 뗌")
            @Size(max = 10, message = "사진은 {max}장까지 붙일 수 있어요.") List<Long> imageIds,
            @Schema(requiredMode = NOT_REQUIRED, description = "더 쓰지 않는다 — 보내도 무시한다(옛 앱 호환)")
            List<Long> quoteIds) {}

    /**
     * 기존 필드는 이름·순서 그대로, 새 필드는 뒤에. clubId·clubName 은 모임 독후감만 채운다.
     * quotes 는 밑줄 기능을 걷어내 늘 빈 목록이다 — 옛 앱이 이 배열을 그대로 읽으므로 필드는 남긴다.
     */
    public record PostView(@NotNull Long id, @NotNull String slug, @NotNull String title, @NotNull String bodyMd,
            @NotNull PostVisibility visibility, List<String> tags,
            Long bookId, String bookTitle, String bookCoverUrl,
            String authorHandle, @NotNull String authorNickname, Instant publishedAt, int viewCount,
            @NotNull Long authorId, String authorAvatarUrl, @NotNull String excerpt,
            List<PostImageView> images, List<BookQuoteView> quotes,
            long likeCount, boolean likedByMe, long commentCount, boolean mine, @NotNull Instant createdAt,
            Long clubId, String clubName) {}

    /** 업로드 응답과 PostView.images 항목이 같이 쓴다. */
    public record PostImageView(@NotNull Long id, @NotNull String url, Integer width, Integer height) {}

    /**
     * 옛 밑줄 항목 — PostView.quotes 의 원소 스키마로만 남는다(늘 빈 목록). 밑줄 기능은 걷어냈다.
     * 스키마 이름(BookQuoteView)과 모양은 옛 앱의 생성 타입과 맞추려고 그대로 둔다.
     */
    public record BookQuoteView(@NotNull Long id, @NotNull Long bookId, @NotNull String bookTitle,
            String bookCoverUrl, Integer page, @NotNull String content,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            long agreeCount, boolean agreedByMe, boolean mine, long commentCount,
            @NotNull Instant createdAt) {}

    /** 좋아요 토글 결과 — BookLikeView 미러. */
    public record PostLikeView(boolean liked, long likeCount) {}

    /** 루트 댓글은 replies 에 답글(오래된 순), 답글 행은 replies = []. */
    public record PostCommentView(@NotNull Long id, @NotNull Long postId, Long parentId,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            @NotNull String body, boolean mine, @NotNull Instant createdAt, List<PostCommentView> replies) {}

    public record CreatePostCommentRequest(@NotBlank @Size(max = 300) String body, Long parentId) {}
}
