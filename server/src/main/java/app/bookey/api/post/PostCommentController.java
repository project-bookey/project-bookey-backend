package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.CreatePostCommentRequest;
import app.bookey.api.post.dto.PostDtos.PostCommentView;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "PostComment", description = "독후감 댓글 — 목록 · 작성(답글 1단계) · 삭제")
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService commentService;

    @Operation(summary = "댓글 목록 — 오래된 순, 루트 댓글에 답글을 묶어 내린다")
    @GetMapping
    public PageResponse<PostCommentView> list(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long postId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        return commentService.list(user.id(), postId, PageRequest.of(page, size));
    }

    @Operation(summary = "댓글 작성 — parentId 를 주면 답글")
    @PostMapping
    public PostCommentView create(@AuthenticationPrincipal AuthUser user,
                                  @PathVariable Long postId,
                                  @Valid @RequestBody CreatePostCommentRequest request) {
        return commentService.create(user.id(), postId, request);
    }

    @Operation(summary = "댓글 삭제 — 본인만, 답글도 함께 지워진다")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long postId,
                                       @PathVariable Long commentId) {
        commentService.delete(user.id(), postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
