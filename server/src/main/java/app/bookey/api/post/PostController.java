package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.*;
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

@Tag(name = "Post", description = "독후감 — 내 기록 아카이브")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "독후감 작성")
    @PostMapping
    public PostView create(@AuthenticationPrincipal AuthUser user,
                           @Valid @RequestBody CreatePostRequest request) {
        return postService.create(user.id(), request);
    }

    @Operation(summary = "내 독후감 목록")
    @GetMapping
    public PageResponse<PostView> listMine(@AuthenticationPrincipal AuthUser user,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return postService.listMine(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "독후감 수정 · 공개 범위 변경")
    @PatchMapping("/{postId}")
    public PostView update(@AuthenticationPrincipal AuthUser user,
                           @PathVariable Long postId,
                           @Valid @RequestBody UpdatePostRequest request) {
        return postService.update(user.id(), postId, request);
    }

    @Operation(summary = "독후감 삭제")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long postId) {
        postService.delete(user.id(), postId);
        return ResponseEntity.noContent().build();
    }
}
