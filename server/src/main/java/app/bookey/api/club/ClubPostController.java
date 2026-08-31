package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.*;
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

@Tag(name = "Club Discussion", description = "모임 토론 — 페이지 앵커 · 스포일러 가드")
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/posts")
@RequiredArgsConstructor
public class ClubPostController {

    private final ClubPostService postService;

    @Operation(summary = "토론 목록 — onlyMyRange=true 면 내 진도까지만 노출")
    @GetMapping
    public PageResponse<PostView> feed(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long clubId,
                                       @RequestParam(defaultValue = "true") boolean onlyMyRange,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return postService.feed(user.id(), clubId, onlyMyRange, PageRequest.of(page, size));
    }

    @Operation(summary = "토론 상세 — 댓글 포함")
    @GetMapping("/{postId}")
    public PostView detail(@AuthenticationPrincipal AuthUser user,
                           @PathVariable Long clubId,
                           @PathVariable Long postId) {
        return postService.detail(user.id(), clubId, postId);
    }

    @Operation(summary = "글 · 댓글 작성")
    @PostMapping
    public PostView create(@AuthenticationPrincipal AuthUser user,
                           @PathVariable Long clubId,
                           @Valid @RequestBody CreatePostRequest request) {
        return postService.create(user.id(), clubId, request);
    }

    @Operation(summary = "스포일러 해제 — '그래도 볼래요'")
    @PostMapping("/{postId}/reveal")
    public PostView reveal(@AuthenticationPrincipal AuthUser user,
                           @PathVariable Long clubId,
                           @PathVariable Long postId) {
        return postService.reveal(user.id(), clubId, postId);
    }

    @Operation(summary = "리액션 토글")
    @PostMapping("/{postId}/reactions")
    public ResponseEntity<Void> react(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long clubId,
                                      @PathVariable Long postId,
                                      @Valid @RequestBody ReactionRequest request) {
        postService.react(user.id(), clubId, postId, request.kind());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "공지 고정 / 해제 (호스트 · 운영자)")
    @PostMapping("/{postId}/pin")
    public ResponseEntity<Void> pin(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long clubId,
                                    @PathVariable Long postId,
                                    @RequestParam(defaultValue = "true") boolean pinned) {
        postService.pin(user.id(), clubId, postId, pinned);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "신고 — 3건 누적 시 임시 비노출")
    @PostMapping("/{postId}/reports")
    public ResponseEntity<Void> report(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long clubId,
                                       @PathVariable Long postId,
                                       @Valid @RequestBody ReportRequest request) {
        postService.report(user.id(), clubId, postId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "삭제 (작성자 또는 운영자)")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long clubId,
                                       @PathVariable Long postId) {
        postService.delete(user.id(), clubId, postId);
        return ResponseEntity.noContent().build();
    }
}
