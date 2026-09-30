package app.bookey.api.post.dto;

import app.bookey.api.quote.dto.QuoteDtos.BookQuoteView;
import app.bookey.domain.post.PostFormat;
import app.bookey.domain.post.PostVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

public final class PostDtos {

    private PostDtos() {}

    /** 피드 정렬 (§14.1) — HOT: 좋아요·시간 감쇠 점수, NEW: 최신순. */
    public enum FeedSort { HOT, NEW }

    /**
     * format 을 생략하면 TEXT. TEXT 는 bodyMd 가 비면 안 되고, NOTE 는 document 가 필수이며 bodyMd 는 빈 문자열이어도 된다
     * (앱이 노트 속 글을 이어 보내 발췌·검색에 쓴다). clubId 를 주면 모임 독후감 — 공개 범위는 PUBLIC·CLUB 만.
     * 사진 상한은 TEXT 10 · NOTE 30 으로 서비스가 형식에 맞춰 다시 검사한다.
     */
    public record CreatePostRequest(Long bookId, Long readingRecordId,
            @NotBlank @Size(max = 300) String title, @NotNull @Size(max = 20000) String bodyMd,
            @NotNull PostVisibility visibility, List<@Size(max = 30) String> tags,
            @Size(max = 30) List<Long> imageIds, @Size(max = 10) List<Long> quoteIds,
            @Schema(requiredMode = NOT_REQUIRED, description = "생략하면 TEXT")
            PostFormat format,
            @Schema(requiredMode = NOT_REQUIRED, description = "NOTE 전용 캔버스 문서 — pages 배열 1~6장, 1MB 이하")
            Map<String, Object> document,
            @Schema(requiredMode = NOT_REQUIRED, description = "모임 안에서 쓸 때 그 모임 id — 활성 멤버·진행 중 모임만")
            Long clubId) {}

    /**
     * null = 유지, 빈 리스트 = 비움. bookId 는 바꿀 수만 있고 없앨 수는 없다.
     * 컬렉션 셋은 생략에 뜻이 있으므로 {@code NOT_REQUIRED} 로 문서에서도 선택 항목으로 내보낸다
     * — 생성 타입이 필수로 나가면 클라이언트가 {@code []} 를 채워 보내 첨부가 전부 지워진다.
     * format·clubId 는 바꿀 수 없어 받지 않는다. document 는 NOTE 글에만 보낼 수 있다.
     */
    public record UpdatePostRequest(Long bookId, @Size(max = 300) String title, @Size(max = 20000) String bodyMd,
            @Schema(requiredMode = NOT_REQUIRED, description = "생략하면 유지, 빈 목록이면 비움")
            List<@Size(max = 30) String> tags,
            PostVisibility visibility,
            @Schema(requiredMode = NOT_REQUIRED, description = "생략하면 유지, 빈 목록이면 사진을 전부 뗌")
            @Size(max = 30) List<Long> imageIds,
            @Schema(requiredMode = NOT_REQUIRED, description = "생략하면 유지, 빈 목록이면 밑줄 연결을 전부 지움")
            @Size(max = 10) List<Long> quoteIds,
            @Schema(requiredMode = NOT_REQUIRED, description = "생략하면 유지 — NOTE 글만, 문서 전체를 덮어쓴다")
            Map<String, Object> document) {}

    /** 기존 필드는 이름·순서 그대로, 새 필드는 뒤에. document 는 NOTE 만, clubId·clubName 은 모임 독후감만 채운다. */
    public record PostView(@NotNull Long id, @NotNull String slug, @NotNull String title, @NotNull String bodyMd,
            @NotNull PostVisibility visibility, List<String> tags,
            Long bookId, String bookTitle, String bookCoverUrl,
            String authorHandle, @NotNull String authorNickname, Instant publishedAt, int viewCount,
            @NotNull Long authorId, String authorAvatarUrl, @NotNull String excerpt,
            List<PostImageView> images, List<BookQuoteView> quotes,
            long likeCount, boolean likedByMe, long commentCount, boolean mine, @NotNull Instant createdAt,
            @NotNull PostFormat format,
            @Schema(requiredMode = NOT_REQUIRED, description = "NOTE 글의 캔버스 문서 — TEXT 는 null")
            Map<String, Object> document,
            Long clubId, String clubName) {}

    /** 업로드 응답과 PostView.images 항목이 같이 쓴다. */
    public record PostImageView(@NotNull Long id, @NotNull String url, Integer width, Integer height) {}

    /** 좋아요 토글 결과 — BookLikeView·QuoteAgreeView 미러. */
    public record PostLikeView(boolean liked, long likeCount) {}

    /** 루트 댓글은 replies 에 답글(오래된 순), 답글 행은 replies = []. */
    public record PostCommentView(@NotNull Long id, @NotNull Long postId, Long parentId,
            @NotNull Long authorId, @NotNull String authorNickname, String authorAvatarUrl,
            @NotNull String body, boolean mine, @NotNull Instant createdAt, List<PostCommentView> replies) {}

    public record CreatePostCommentRequest(@NotBlank @Size(max = 300) String body, Long parentId) {}
}
