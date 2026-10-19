package app.bookey.api.novel;

import app.bookey.api.auth.WriteBanGuarded;
import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.novel.*;
import io.swagger.v3.oas.annotations.Operation;
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
    private final NovelHomeFeedService homeFeedService;
    private final NovelRefreshPurchaseService refreshPurchaseService;
    private final NovelBranchBinService binService;
    private final NovelSunlightService sunlightService;
    private final NovelSunlightPurchaseService sunlightPurchaseService;
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
    @Operation(summary = "홈 열매 밭 — 지금 놓인 묶음과 오늘 남은 무료·광고 새로고침, 결제한 새로고침. 처음이거나 묶음이 비어 있으면 최신순으로 첫 묶음을 만든다(횟수 차감 없음)")
    @GetMapping("/home-feed")
    public NovelHomeFeedView homeFeed(@AuthenticationPrincipal AuthUser user) { return homeFeedService.current(user.id()); }
    /** 좋아요처럼 쓰기 제재와 상관없이 누를 수 있다. 하루 무료·광고 횟수가 남용을 막는다. */
    @Operation(summary = "홈 열매 밭 새로고침 — 오늘 무료 횟수가 남았으면 무료로, 다 썼으면 결제한 횟수로 새 묶음으로 바꾼다(광고 횟수는 쓰지 않는다). 둘 다 없으면 429, 새로 놓을 작품이 없으면 409(차감 없음)")
    @PostMapping("/home-feed/refresh")
    public NovelHomeFeedView refreshHomeFeed(@AuthenticationPrincipal AuthUser user) { return homeFeedService.refresh(user.id()); }
    @Operation(summary = "광고를 보고 새로고침 — 광고를 끝까지 본 뒤 부른다. 무료 횟수가 남았으면 무료를, 없으면 오늘 광고 횟수를 쓴다. 광고 횟수도 다 쓰면 429(NOVEL_REFRESH_AD_LIMIT), 새로 놓을 작품이 없으면 409(차감 없음)")
    @PostMapping("/home-feed/refresh/ad")
    public NovelHomeFeedView refreshHomeFeedWithAd(@AuthenticationPrincipal AuthUser user) { return homeFeedService.refreshWithAd(user.id()); }
    @Operation(summary = "새로고침 결제 주문 — 오늘 무료·광고 횟수를 다 썼고 결제해 둔 횟수도 없을 때만 연다(아니면 409 NOVEL_REFRESH_STILL_LEFT). APPLE·GOOGLE 만")
    @PostMapping("/home-feed/refresh-purchases/checkout")
    public NovelRefreshCheckoutView checkoutRefresh(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody NovelRefreshCheckoutRequest request) {
        return refreshPurchaseService.checkout(user.id(), request);
    }
    @Operation(summary = "새로고침 결제 확인 — 스토어 결제를 확인하면 결제한 새로고침 하나를 더한다. 새로고침은 하지 않으니 이어서 /home-feed/refresh 를 부른다. 이미 확인한 주문이면 더하지 않고 지금 상태를 돌려준다")
    @PostMapping("/home-feed/refresh-purchases/verify")
    public NovelHomeFeedView verifyRefresh(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody NovelRefreshVerifyRequest request) {
        return refreshPurchaseService.verify(user.id(), request);
    }
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
    @Operation(summary = "나뭇가지 수거함 — 한 달 동안 조회가 50회를 못 넘겨 나무에서 숨긴 내 가지. 지울 날(purgeAt) 전에 살리거나 버린다")
    @GetMapping("/me/branch-bin")
    public NovelBranchBinView branchBin(@AuthenticationPrincipal AuthUser user) { return binService.bin(user.id()); }
    @Operation(summary = "수거함의 가지 살리기 — 햇살을 내고 나무로 되돌린다. 살린 날부터 한 달 뒤 다시 판정. 햇살이 모자라면 402")
    @PostMapping("/me/branch-bin/{chapterId}/restore")
    public NovelBranchBinView restoreBranch(@AuthenticationPrincipal AuthUser user, @PathVariable Long chapterId) {
        return binService.restore(user.id(), chapterId);
    }
    @Operation(summary = "수거함의 가지 버리기 — 바로 지우고 부모 회차의 가지 자리를 비운다")
    @PostMapping("/me/branch-bin/{chapterId}/discard")
    public NovelBranchBinView discardBranch(@AuthenticationPrincipal AuthUser user, @PathVariable Long chapterId) {
        return binService.discard(user.id(), chapterId);
    }
    @Operation(summary = "햇살 잔액·쓰는 값·충전 상품")
    @GetMapping("/me/sunlight")
    public NovelSunlightView sunlight(@AuthenticationPrincipal AuthUser user) { return sunlightService.view(user.id()); }
    @Operation(summary = "햇살 충전 주문 — 앱 안 결제(APPLE·GOOGLE)만")
    @PostMapping("/sunlight-purchases/checkout")
    public NovelSunlightCheckoutView checkoutSunlight(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody NovelSunlightCheckoutRequest request) {
        return sunlightPurchaseService.checkout(user.id(), request);
    }
    @Operation(summary = "햇살 충전 확인 — 스토어에 거래를 확인하면 햇살을 넣는다. 같은 주문은 한 번만 넣는다")
    @PostMapping("/sunlight-purchases/verify")
    public NovelSunlightView verifySunlight(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody NovelSunlightVerifyRequest request) {
        return sunlightPurchaseService.verify(user.id(), request);
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
    @Operation(summary = "내 글 잇기 — 햇살을 내고 내가 쓴 회차를 나에게 배달한다(마감 7일). 넘기거나 마감이 지나면 햇살을 돌려받는다")
    @PostMapping("/{id}/chapters/{chapterId}/self-delivery") @WriteBanGuarded
    public NovelDeliveryView selfDelivery(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long chapterId) {
        return service.sendToSelf(user.id(), id, chapterId);
    }
    @GetMapping("/{id}/chapters/{number}")
    public NovelChapterView chapter(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable int number) {
        return service.chapter(user.id(), id, number);
    }
}
