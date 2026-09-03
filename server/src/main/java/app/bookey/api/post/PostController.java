package app.bookey.api.post;

import app.bookey.api.post.dto.PostDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Post", description = "독후감 — 내 기록 아카이브 · 광장 피드")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostImageService postImageService;

    @Operation(summary = "독후감 작성")
    @PostMapping
    public PostView create(@AuthenticationPrincipal AuthUser user,
                           @Valid @RequestBody CreatePostRequest request) {
        return postService.create(user.id(), request);
    }

    /** 리터럴 {@code images} 는 {@code /{postId}} 보다 먼저 매칭된다. */
    @Operation(summary = "독후감 사진 업로드 — 글에 붙이기 전 임시 저장, 24시간 안에 안 붙이면 삭제")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostImageView upload(@AuthenticationPrincipal AuthUser user,
                                @RequestPart("file") MultipartFile file) {
        return postImageService.upload(user.id(), file);
    }

    @Operation(summary = "내 독후감 목록")
    @GetMapping
    public PageResponse<PostView> listMine(@AuthenticationPrincipal AuthUser user,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return postService.listMine(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "광장 독후감 피드 — 공개 독후감 최신순")
    @GetMapping("/feed")
    public PageResponse<PostView> feed(@AuthenticationPrincipal AuthUser user,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return postService.feed(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "독후감 한 건 — 비공개는 작성자만, 남의 글은 조회수를 올린다")
    @GetMapping("/{postId}")
    public PostView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long postId) {
        return postService.get(user.id(), postId);
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

    @Operation(summary = "좋아요 토글")
    @PostMapping("/{postId}/like")
    public PostLikeView like(@AuthenticationPrincipal AuthUser user, @PathVariable Long postId) {
        return postService.toggleLike(user.id(), postId);
    }
}
