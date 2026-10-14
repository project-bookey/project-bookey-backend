package app.bookey.api.novel;

import app.bookey.api.auth.WriteBanGuarded;
import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.novel.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequestMapping("/api/v1/novels") @RequiredArgsConstructor
public class NovelController {
    private final NovelService service;
    private final NovelCoverService coverService;
    private final NovelBranchService branchService;
    private final NovelGenreService genreService;
    @GetMapping
    public PageResponse<NovelSummary> feed(@AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "SOLO") NovelKind kind, @RequestParam(required = false) NovelStatus status,
            @RequestParam(defaultValue = "0") int page) {
        return service.feed(user.id(), kind, status, PageRequest.of(Math.max(0, page), 20));
    }
    @GetMapping("/mine")
    public PageResponse<NovelSummary> mine(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "SOLO") NovelKind kind) {
        return service.mine(user.id(), kind, PageRequest.of(Math.max(0, page), 20));
    }
    @GetMapping("/my-turns")
    public List<NovelSummary> myTurns(@AuthenticationPrincipal AuthUser user) { return service.myTurns(user.id()); }
    /** 내가 받은 열매 — 마감 전이고 연재 중인 것만. */
    @GetMapping("/deliveries")
    public List<NovelDeliveryView> deliveries(@AuthenticationPrincipal AuthUser user) { return service.deliveries(user.id()); }
    @GetMapping("/deliveries/{deliveryId}/draft")
    public NovelDraftView deliveryDraft(@AuthenticationPrincipal AuthUser user, @PathVariable Long deliveryId) {
        return branchService.draft(user.id(), deliveryId);
    }
    @PutMapping("/deliveries/{deliveryId}/draft") @WriteBanGuarded
    public NovelDraftView saveDeliveryDraft(@AuthenticationPrincipal AuthUser user, @PathVariable Long deliveryId,
            @Valid @RequestBody NovelBranchWriteRequest request) {
        return branchService.saveDraft(user.id(), deliveryId, request);
    }
    /** 받은 회차에서 가지를 뻗어 발행한다. 새 회차는 다시 무작위로 배달된다. */
    @PostMapping("/deliveries/{deliveryId}/chapters") @WriteBanGuarded
    public NovelChapterView publishBranch(@AuthenticationPrincipal AuthUser user, @PathVariable Long deliveryId,
            @Valid @RequestBody NovelBranchWriteRequest request) {
        return branchService.publish(user.id(), deliveryId, request);
    }
    /** 넘기기 — 다른 한 사람에게 다시 배달된다. */
    @PostMapping("/deliveries/{deliveryId}/decline")
    public void decline(@AuthenticationPrincipal AuthUser user, @PathVariable Long deliveryId) {
        branchService.decline(user.id(), deliveryId);
    }
    @GetMapping("/genres")
    public List<String> genres() { return genreService.genres(); }
    @GetMapping("/me/genres")
    public List<String> myGenres(@AuthenticationPrincipal AuthUser user) { return genreService.mine(user.id()); }
    @PutMapping("/me/genres")
    public List<String> changeGenres(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody NovelGenresRequest request) {
        return genreService.replace(user.id(), request);
    }
    @GetMapping("/base-works")
    public List<NovelBaseWorkView> baseWorks() { return genreService.baseWorks(); }
    @PostMapping @WriteBanGuarded
    public NovelDetail create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateNovelRequest request) {
        return service.create(user.id(), request);
    }
    @PostMapping(value = "/covers", consumes = "multipart/form-data") @WriteBanGuarded
    public NovelCoverView upload(@AuthenticationPrincipal AuthUser user, @RequestPart("file") MultipartFile file) {
        return coverService.upload(user.id(), file);
    }
    @GetMapping("/{id}")
    public NovelDetail detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { return service.get(user.id(), id); }
    /** 좋아요 토글 — 책·독후감 좋아요처럼 쓰기 제재와 상관없이 누를 수 있다. */
    @PostMapping("/{id}/like")
    public NovelLikeView like(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { return service.toggleLike(user.id(), id); }
    @PutMapping("/{id}/cover") @WriteBanGuarded
    public NovelDetail cover(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody NovelCoverRequest request) {
        return service.changeCover(user.id(), id, request.coverId());
    }
    @PostMapping("/{id}/applications") @WriteBanGuarded
    public NovelDetail apply(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { return service.apply(user.id(), id); }
    @PutMapping("/{id}/applications/{userId}") @WriteBanGuarded
    public NovelDetail decide(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long userId,
            @Valid @RequestBody NovelMemberDecision decision) { return service.decide(user.id(), id, userId, decision.approved()); }
    @PostMapping("/{id}/start") @WriteBanGuarded
    public NovelDetail start(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { return service.start(user.id(), id); }
    @PostMapping("/{id}/complete") @WriteBanGuarded
    public NovelDetail complete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { return service.complete(user.id(), id); }
    @DeleteMapping("/{id}/membership")
    public void leave(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { service.leave(user.id(), id); }
    @GetMapping("/{id}/draft")
    public NovelDraftView draft(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { return service.draft(user.id(), id); }
    @PutMapping("/{id}/draft") @WriteBanGuarded
    public NovelDraftView save(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody NovelWriteRequest request) {
        return service.saveDraft(user.id(), id, request);
    }
    @PostMapping("/{id}/chapters") @WriteBanGuarded
    public NovelChapterView publish(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody NovelWriteRequest request) {
        return service.publish(user.id(), id, request);
    }
    @GetMapping("/{id}/chapters")
    public PageResponse<NovelChapterSummary> chapters(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page) { return service.chapters(user.id(), id, PageRequest.of(Math.max(0, page), 20)); }
    @GetMapping("/{id}/chapter-tree")
    public List<NovelChapterNode> chapterTree(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.chapterTree(user.id(), id);
    }
    @GetMapping("/{id}/chapters/nodes/{chapterId}")
    public NovelChapterView chapterNode(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long chapterId) {
        return service.chapterNode(user.id(), id, chapterId);
    }
    @GetMapping("/{id}/chapters/{number}")
    public NovelChapterView chapter(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable int number) {
        return service.chapter(user.id(), id, number);
    }
}
