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
    @PostMapping @WriteBanGuarded
    public NovelDetail create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateNovelRequest request) {
        return service.create(user.id(), request);
    }
    @PostMapping(value = "/covers", consumes = "multipart/form-data") @WriteBanGuarded
    public NovelCoverView upload(@AuthenticationPrincipal AuthUser user, @RequestPart("file") MultipartFile file) {
        return coverService.upload(user.id(), file);
    }
    @PostMapping("/join") @WriteBanGuarded
    public NovelDetail join(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody NovelJoinRequest request) {
        return service.joinByCode(user.id(), request.inviteCode());
    }
    @GetMapping("/{id}")
    public NovelDetail detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) { return service.get(user.id(), id); }
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
    @GetMapping("/{id}/chapters/{number}")
    public NovelChapterView chapter(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable int number) {
        return service.chapter(user.id(), id, number);
    }
}
