package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.common.support.*;
import app.bookey.domain.novel.*;
import app.bookey.domain.social.UserFollow;
import app.bookey.domain.social.UserFollowRepository;
import app.bookey.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class NovelService {
    /** 내 차례·받은 열매 목록이 한 번에 내려 주는 수. 홈 열매 밭은 이 목록을 피드 묶음보다 먼저 놓는다. */
    static final Pageable HOME_LIST_PAGE = PageRequest.of(0, 20);
    private final NovelRepository novels;
    private final NovelMemberRepository members;
    private final NovelChapterRepository chapters;
    private final NovelDraftRepository drafts;
    private final NovelCoverRepository covers;
    private final NovelLikeRepository likes;
    private final UserRepository users;
    private final RateLimiter limiter;
    private final NovelDeliveryRepository deliveries;
    private final NovelDeliveryService delivery;
    private final NovelBaseWorkRepository baseWorks;
    private final UserFollowRepository follows;
    private final NovelSunlightService sunlight;

    @Transactional
    public NovelDetail create(Long me, CreateNovelRequest request) {
        limiter.require("novel:create:" + me, 5, Duration.ofMinutes(1));
        NovelMode mode = request.mode() == null ? NovelMode.CIRCLE : request.mode();
        if (mode == NovelMode.OPEN && request.kind() != NovelKind.RELAY) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        Long baseWorkId = requireBaseWork(request.baseWorkId());
        int chapterLimit = request.chapterLimit() == null ? 20 : request.chapterLimit();
        int turnHours = request.turnHours() == null ? 24 : request.turnHours();
        List<Long> invitees = mode == NovelMode.CIRCLE ? invitees(me, request.inviteeIds()) : List.of();
        Novel created;
        if (mode == NovelMode.OPEN) {
            int branchLimit = request.branchLimit() == null ? Novel.BRANCH_LIMIT_MIN : request.branchLimit();
            created = Novel.open(me, request.title(), request.description(), request.genre(), chapterLimit, turnHours,
                    branchLimit, baseWorkId);
        } else {
            // 함께 쓸 작가를 고르면 정원은 고른 사람과 나다. 고르지 않으면 예전처럼 모집한다.
            int memberLimit = invitees.isEmpty() ? (request.memberLimit() == null ? 6 : request.memberLimit()) : invitees.size() + 1;
            created = new Novel(me, request.kind(), request.title(), request.description(), request.genre(),
                    memberLimit, chapterLimit, turnHours, request.isPublic());
            created.useBaseWork(baseWorkId);
        }
        Novel n = novels.saveAndFlush(created);
        members.saveAndFlush(new NovelMember(n.getId(), me, true));
        invitees.forEach(id -> members.save(new NovelMember(n.getId(), id, true)));
        members.flush();
        attachCover(n, me, request.coverId());
        return detail(n, me);
    }
    private Long requireBaseWork(Long id) {
        if (id == null) return null;
        return baseWorks.findById(id).filter(NovelBaseWork::isActive).map(NovelBaseWork::getId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
    /** 모여 만들기는 내가 팔로우한, 탈퇴하지 않은 작가만 고를 수 있다. */
    private List<Long> invitees(Long me, List<Long> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        List<Long> ids = requested.stream().filter(Objects::nonNull).filter(id -> !id.equals(me)).distinct().toList();
        Set<Long> followed = follows.findAllByFollowerIdAndFolloweeIdIn(me, ids).stream()
                .map(UserFollow::getFolloweeId).collect(Collectors.toSet());
        if (!followed.containsAll(ids) || ids.stream().anyMatch(users::isTerminated))
            throw ApiException.of(ErrorCode.NOVEL_INVITEE_NOT_FOLLOWED);
        return ids;
    }

    @Transactional(readOnly = true)
    public PageResponse<NovelSummary> feed(Long me, NovelKind kind, NovelStatus status, Pageable page) {
        Page<Novel> found = novels.feed(kind, status, page);
        LikeStats likeStats = likeStats(found.getContent(), me);
        return PageResponse.of(found, n -> summary(n, me, likeStats));
    }
    @Transactional(readOnly = true)
    public PageResponse<NovelSummary> mine(Long me, NovelKind kind, Pageable page) {
        Page<Novel> found = novels.mine(me, kind, page);
        LikeStats likeStats = likeStats(found.getContent(), me);
        return PageResponse.of(found, n -> summary(n, me, likeStats));
    }
    @Transactional(readOnly = true)
    public List<NovelSummary> myTurns(Long me) {
        return summaries(novels.myTurns(me, Instant.now(), HOME_LIST_PAGE), me);
    }
    /** 작품 목록을 순서 그대로 지금 사용자 기준 요약으로 바꾼다. 좋아요는 목록 전체를 한 번에 읽는다. */
    public List<NovelSummary> summaries(List<Novel> found, Long me) {
        LikeStats likeStats = likeStats(found, me);
        return found.stream().map(n -> summary(n, me, likeStats)).toList();
    }
    /** 내가 받은 열매 — 홈 열매 밭에 떨어진다. */
    @Transactional(readOnly = true)
    public List<NovelDeliveryView> deliveries(Long me) {
        List<NovelDelivery> held = deliveries.holding(me, Instant.now(), HOME_LIST_PAGE);
        Map<Long, Novel> byId = novels.findAllById(held.stream().map(NovelDelivery::getNovelId).toList()).stream()
                .collect(Collectors.toMap(Novel::getId, n -> n));
        LikeStats likeStats = likeStats(List.copyOf(byId.values()), me);
        List<NovelDeliveryView> views = new ArrayList<>();
        for (NovelDelivery d : held) {
            Novel n = byId.get(d.getNovelId());
            NovelChapter c = chapters.findById(d.getChapterId()).filter(NovelChapter::isActive).orElse(null);
            if (n == null || c == null) continue;
            views.add(deliveryView(d, n, c, me, likeStats));
        }
        return views;
    }
    private NovelDeliveryView deliveryView(NovelDelivery d, Novel n, NovelChapter c, Long me, LikeStats likeStats) {
        return new NovelDeliveryView(d.getId(), summary(n, me, likeStats), c.getId(), c.getChapterNumber(),
                c.getTitle(), d.getDueAt(), d.getSource());
    }
    /** 내 글 잇기 — 햇살을 내고 내가 쓴 회차를 나에게 배달한다. 그 뒤로는 받은 열매처럼 쓰고 발행한다. */
    @Transactional
    public NovelDeliveryView sendToSelf(Long me, Long id, Long chapterId) {
        limiter.require("novel:self-branch:" + me, 10, Duration.ofMinutes(1));
        Novel n = readable(me, id, true);
        NovelChapter c = chapters.findByIdAndNovelId(chapterId, id).filter(NovelChapter::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        NovelDelivery d = delivery.sendToSelf(n, c, me, Instant.now());
        return deliveryView(d, n, c, me, likeStats(List.of(n), me));
    }
    /** 책·독후감 좋아요처럼 누를 때마다 켜고 끈다. 볼 수 있는 작품에만 누를 수 있다. */
    @Transactional
    public NovelLikeView toggleLike(Long me, Long id) {
        readable(me, id, false);
        var existing = likes.findByUserIdAndNovelId(me, id);
        if (existing.isPresent()) likes.delete(existing.get());
        else likes.save(NovelLike.builder().userId(me).novelId(id).build());
        return new NovelLikeView(existing.isEmpty(), likes.countByNovelId(id));
    }
    @Transactional
    public NovelDetail get(Long me, Long id) {
        Novel n = readable(me, id, true);
        expire(n);
        return detail(n, me);
    }
    @Transactional
    public NovelDetail changeCover(Long me, Long id, Long coverId) {
        Novel n = readable(me, id, true); requireOwner(n, me);
        attachCover(n, me, coverId); return detail(n, me);
    }
    private void attachCover(Novel n, Long me, Long id) {
        if (Objects.equals(n.getCoverId(), id)) return;
        NovelCover cover = id == null ? null : covers.lockById(id).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (cover != null && (!cover.getUserId().equals(me) || cover.getNovelId() != null))
            throw ApiException.of(ErrorCode.FORBIDDEN);
        if (n.getCoverId() != null) covers.lockById(n.getCoverId()).ifPresent(NovelCover::detach);
        if (cover != null) cover.attach(n.getId());
        n.changeCover(id);
    }

    /** 공개 작품은 누구나 신청한다. 승인 전에는 집필 순서에 들어가지 않는다. 비공개 작품은 만들 때 고른 작가만 참가한다. */
    @Transactional
    public NovelDetail apply(Long me, Long id) {
        Novel n = readable(me, id, true);
        if (!n.isPublic() && !n.isOwner(me)) throw ApiException.of(ErrorCode.FORBIDDEN);
        return applyTo(n, me);
    }
    private NovelDetail applyTo(Novel n, Long me) {
        if (n.getKind() != NovelKind.RELAY || n.isOpen() || n.getStatus() == NovelStatus.COMPLETED)
            throw new ApiException(ErrorCode.CONFLICT, "참가자를 받고 있지 않은 작품이에요.");
        var existing = members.findByNovelIdAndUserId(n.getId(), me);
        if (existing.isPresent() && List.of("ACTIVE", "PENDING").contains(existing.get().getStatus()))
            return detail(n, me);
        limiter.require("novel:apply:" + me, 10, Duration.ofMinutes(1));
        if (isFull(n, active(n).size())) throw ApiException.of(ErrorCode.NOVEL_FULL);
        NovelMember member = existing.orElseGet(() -> new NovelMember(n.getId(), me, false));
        member.apply(); members.saveAndFlush(member);
        return detail(n, me);
    }
    @Transactional
    public NovelDetail decide(Long me, Long id, Long applicant, boolean approved) {
        Novel n = readable(me, id, true); requireOwner(n, me); expire(n);
        if (n.getStatus() == NovelStatus.COMPLETED) throw ApiException.of(ErrorCode.NOVEL_CLOSED);
        NovelMember m = members.findByNovelIdAndUserId(id, applicant)
                .filter(v -> "PENDING".equals(v.getStatus()) && !users.isTerminated(v.getUserId()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (approved) {
            if (isFull(n, active(n).size())) throw ApiException.of(ErrorCode.NOVEL_FULL);
            m.approve();
        } else m.reject();
        members.flush(); return detail(n, me);
    }
    @Transactional
    public NovelDetail start(Long me, Long id) {
        Novel n = readable(me, id, true); requireOwner(n, me);
        if (n.getKind() != NovelKind.RELAY || n.getStatus() != NovelStatus.RECRUITING || active(n).size() < 2)
            throw new ApiException(ErrorCode.CONFLICT, "참가자를 한 명 이상 승인한 뒤 시작해 주세요.");
        n.start(Instant.now()); return detail(n, me);
    }
    @Transactional
    public NovelDetail complete(Long me, Long id) {
        Novel n = readable(me, id, true); requireOwner(n, me);
        if (n.getChapterCount() == 0) throw new ApiException(ErrorCode.CONFLICT, "첫 회차를 올린 뒤 완결할 수 있어요.");
        n.complete(); delivery.closeAll(n); return detail(n, me);
    }
    @Transactional
    public void leave(Long me, Long id) {
        Novel n = readable(me, id, true); expire(n);
        if (n.isOwner(me)) throw new ApiException(ErrorCode.CONFLICT, "개설자는 작품을 완결한 뒤에도 참가자로 남아요.");
        NovelMember m = members.findByNovelIdAndUserId(id, me).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (n.canWrite(me)) {
            // 먼저 다음 참가자를 정한 뒤 현재 참가자를 제외한다 — 중간 순번도 건너뛰지 않는다.
            n.skip(writerIds(n), Instant.now());
        }
        m.leave(); members.flush();
    }

    @Transactional
    public NovelDraftView draft(Long me, Long id) {
        Novel n = readable(me, id, true); expire(n); requireWrite(n, me, n.getTurnNumber());
        return drafts.findByNovelIdAndUserId(id, me).filter(d -> d.getTurnNumber() == n.getTurnNumber())
                .map(d -> new NovelDraftView(d.getTurnNumber(), d.getTitle(), d.getBody(), d.getUpdatedAt()))
                .orElseGet(() -> new NovelDraftView(n.getTurnNumber(), "", "", null));
    }
    @Transactional
    public NovelDraftView saveDraft(Long me, Long id, NovelWriteRequest request) {
        Novel n = readable(me, id, true); expire(n); requireWrite(n, me, request.turnNumber());
        NovelDraft d = drafts.findByNovelIdAndUserId(id, me).orElseGet(() -> new NovelDraft(id, me));
        d.save(n.getTurnNumber(), request.title(), request.body());
        drafts.saveAndFlush(d);
        return new NovelDraftView(d.getTurnNumber(), d.getTitle(), d.getBody(), d.getUpdatedAt());
    }
    @Transactional
    public NovelChapterView publish(Long me, Long id, NovelWriteRequest request) {
        Novel n = readable(me, id, true); expire(n); requireWrite(n, me, request.turnNumber());
        if (request.title().isBlank() || request.body().isBlank()) throw ApiException.of(ErrorCode.INVALID_REQUEST);
        // 한 줄기로 이어지는 작품은 마지막 회차가 부모다. 열매로 퍼뜨리는 작품은 여기서 뿌리 회차만 나온다.
        Long parentId = n.getChapterCount() == 0 ? null
                : chapters.findFirstByNovelIdOrderByChapterNumberDescIdDesc(id).map(NovelChapter::getId).orElse(null);
        NovelChapter c = chapters.saveAndFlush(new NovelChapter(id, me, parentId, n.getChapterCount() + 1,
                request.title(), request.body()));
        drafts.findByNovelIdAndUserId(id, me).ifPresent(drafts::delete);
        Instant now = Instant.now();
        n.publish(writerIds(n), now);
        if (n.isOpen()) delivery.deliverNewChapter(n, c, now);
        return chapterView(c, n, me);
    }
    @Transactional(readOnly = true)
    public PageResponse<NovelChapterSummary> chapters(Long me, Long id, Pageable page) {
        requireReadChapters(me, id);
        return PageResponse.of(chapters.findByNovelIdAndStateOrderByChapterNumberAscIdAsc(id, NovelChapterState.ACTIVE, page),
                c -> new NovelChapterSummary(c.getId(), c.getChapterNumber(), c.getTitle(),
                        nickname(c.getAuthorId()), excerpt(c.getBody()), c.getCreatedAt()));
    }
    @Transactional(readOnly = true)
    public NovelChapterView chapter(Long me, Long id, int number) {
        Novel n = requireReadChapters(me, id);
        return chapterView(chapters.findFirstByNovelIdAndChapterNumberAndStateOrderByIdAsc(id, number, NovelChapterState.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)), n, me);
    }
    /**
     * 가지가 갈라진 작품은 번호(깊이)로 회차를 하나로 정할 수 없어 id 로 읽는다. 앱이 회차를 열 때 부르므로 여기서 조회수를 센다 —
     * 글쓴이 본인은 빼고 한 사람당 한 번. 수거함에 들어간 가지는 나무에서 숨기므로 찾지 못한다.
     */
    @Transactional
    public NovelChapterView chapterNode(Long me, Long id, Long chapterId) {
        Novel n = requireReadChapters(me, id);
        NovelChapter c = chapters.findByIdAndNovelId(chapterId, id).filter(NovelChapter::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        int viewCount = c.getViewCount();
        if (!c.isWrittenBy(me) && chapters.recordView(c.getId(), me) > 0) {
            chapters.incrementViews(c.getId());
            viewCount++;
        }
        return chapterView(c, n, me, viewCount);
    }
    /** 회차 나무 전체 — 마디마다 부모 회차를 실어, 앱이 가지와 끝 열매를 그린다. */
    @Transactional(readOnly = true)
    public List<NovelChapterNode> chapterTree(Long me, Long id) {
        requireReadChapters(me, id);
        List<NovelChapter> all = chapters.findByNovelIdAndStateOrderByIdAsc(id, NovelChapterState.ACTIVE);
        Map<Long, String> nicknames = nicknames(all.stream().map(NovelChapter::getAuthorId).filter(Objects::nonNull).distinct().toList());
        return all.stream().map(c -> new NovelChapterNode(c.getId(), c.getParentChapterId(), c.getChapterNumber(),
                c.getTitle(), c.getAuthorId() == null ? "탈퇴한 작가" : nicknames.getOrDefault(c.getAuthorId(), "탈퇴한 작가"),
                excerpt(c.getBody()), c.getCreatedAt())).toList();
    }
    private NovelChapterView chapterView(NovelChapter c, Novel n, Long me) { return chapterView(c, n, me, c.getViewCount()); }
    private NovelChapterView chapterView(NovelChapter c, Novel n, Long me, int viewCount) {
        return new NovelChapterView(c.getId(), c.getNovelId(), c.getChapterNumber(), c.getTitle(),
                c.getBody(), nickname(c.getAuthorId()), c.getCreatedAt(), c.getParentChapterId(), viewCount,
                delivery.canSendToSelf(n, c, me, Instant.now()), sunlight.selfBranchCost());
    }
    @Transactional
    public void expire(Long id) { novels.lockById(id).ifPresent(this::expire); }
    private void expire(Novel n) {
        var ids = writerIds(n);
        n.expire(ids, Instant.now());
        if (n.getKind() == NovelKind.RELAY && !n.isOpen() && n.getStatus() == NovelStatus.ONGOING
                && !ids.contains(n.getCurrentWriterId())) n.skip(ids, Instant.now());
    }
    private Novel requireReadChapters(Long me, Long id) {
        Novel n = readable(me, id, false);
        if (!n.isPublic() && !n.isOwner(me) && members.findByNovelIdAndUserId(id, me)
                .filter(m -> "ACTIVE".equals(m.getStatus())).isEmpty()) throw ApiException.of(ErrorCode.FORBIDDEN);
        return n;
    }
    private void requireWrite(Novel n, Long me, long turn) {
        if (!n.canWrite(me) || n.getTurnNumber() != turn) throw ApiException.of(ErrorCode.NOVEL_NOT_YOUR_TURN);
    }
    private void requireOwner(Novel n, Long me) { if (!n.isOwner(me)) throw ApiException.of(ErrorCode.FORBIDDEN); }
    private Novel readable(Long me, Long id, boolean lock) {
        Novel n = (lock ? novels.lockById(id) : novels.findById(id)).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (users.isTerminated(n.getOwnerId())) throw ApiException.of(ErrorCode.NOT_FOUND);
        if (!n.isPublic() && !n.isOwner(me) && members.findByNovelIdAndUserId(id, me)
                .filter(m -> List.of("ACTIVE", "PENDING").contains(m.getStatus())).isEmpty())
            throw ApiException.of(ErrorCode.NOT_FOUND);
        return n;
    }
    private List<NovelMember> active(Novel n) { return members.activeMembers(n.getId()); }
    private List<Long> writerIds(Novel n) { return active(n).stream().map(NovelMember::getUserId).toList(); }
    private boolean isFull(Novel n, int activeCount) { return n.getMemberLimit() != null && activeCount >= n.getMemberLimit(); }
    private Map<Long, String> nicknames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.findAllById(ids).stream().filter(u -> u.getStatus() != UserStatus.TERMINATED)
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }
    private String nickname(Long id) {
        return id == null ? "탈퇴한 작가" : users.findById(id).filter(u -> u.getStatus() != UserStatus.TERMINATED)
                .map(User::getNickname).orElse("탈퇴한 작가");
    }
    private String excerpt(String body) { return body.substring(0, Math.min(140, body.length())); }
    private NovelMemberView member(Novel n, NovelMember m) {
        User u = users.findById(m.getUserId()).orElse(null);
        return new NovelMemberView(m.getUserId(), nickname(m.getUserId()), u == null ? null : u.getAvatarUrl(),
                m.getStatus(), n.isOwner(m.getUserId()), Objects.equals(n.getCurrentWriterId(), m.getUserId()));
    }
    private NovelSummary summary(Novel n, Long me) { return summary(n, me, likeStats(List.of(n), me)); }
    private NovelSummary summary(Novel n, Long me, LikeStats likeStats) {
        String url = n.getCoverId() == null ? null : covers.findById(n.getCoverId()).map(NovelCover::getUrl).orElse(null);
        boolean myTurn = n.canWrite(me) && (n.getTurnDueAt() == null || Instant.now().isBefore(n.getTurnDueAt()));
        return new NovelSummary(n.getId(), n.getKind(), n.getStatus(), n.getTitle(), n.getDescription(), n.getGenre(),
                url, nickname(n.getOwnerId()), memberCount(n), n.getMemberLimit(), n.getChapterCount(),
                n.getChapterLimit(), n.isPublic(), n.isOwner(me), myTurn, n.getTurnDueAt(), n.getCreatedAt(),
                likeStats.count(n.getId()), likeStats.liked(n.getId()), n.getMode(), n.getDeliveryCount(),
                n.isOpen() ? n.getBranchLimit() : null);
    }
    /** 열매로 퍼뜨리는 작품은 참가 신청이 없으니 회차를 쓴 작가 수를 센다. */
    private int memberCount(Novel n) {
        if (!n.isOpen()) return active(n).size();
        return (int) Math.max(1, chapters.countAuthors(n.getId()));
    }
    /** 작품들의 좋아요 수와 내가 누른 작품. 목록 한 페이지를 줄마다 세지 않고 한 번에 읽는다. */
    private record LikeStats(Map<Long, Long> counts, Set<Long> mine) {
        long count(Long id) { return counts.getOrDefault(id, 0L); }
        boolean liked(Long id) { return mine.contains(id); }
    }
    private LikeStats likeStats(List<Novel> found, Long me) {
        List<Long> ids = found.stream().map(Novel::getId).toList();
        if (ids.isEmpty()) return new LikeStats(Map.of(), Set.of());
        Map<Long, Long> counts = likes.countPerNovel(ids).stream().collect(Collectors.toMap(
                NovelLikeRepository.NovelLikeCount::getNovelId, NovelLikeRepository.NovelLikeCount::getLikeCount));
        Set<Long> mine = likes.findAllByUserIdAndNovelIdIn(me, ids).stream()
                .map(NovelLike::getNovelId).collect(Collectors.toSet());
        return new LikeStats(counts, mine);
    }
    private NovelDetail detail(Novel n, Long me) {
        var active = active(n);
        String membership = members.findByNovelIdAndUserId(n.getId(), me).map(NovelMember::getStatus).orElse("NONE");
        var applications = n.isOwner(me) ? members.findByNovelIdAndStatusOrderByIdAsc(n.getId(), "PENDING").stream()
                .filter(m -> !users.isTerminated(m.getUserId())).map(m -> member(n, m)).toList() : List.<NovelMemberView>of();
        return new NovelDetail(summary(n, me), active.stream().map(m -> member(n, m)).toList(), applications, membership,
                n.isPublic() && n.getKind() == NovelKind.RELAY && !n.isOpen() && n.getStatus() != NovelStatus.COMPLETED
                        && !List.of("ACTIVE", "PENDING").contains(membership) && !isFull(n, active.size()),
                n.canWrite(me), n.isOwner(me) && n.getStatus() == NovelStatus.RECRUITING && active.size() >= 2,
                n.isOwner(me) && n.getStatus() == NovelStatus.ONGOING && n.getChapterCount() > 0,
                n.getTurnNumber(), n.getCurrentWriterId() == null ? null : nickname(n.getCurrentWriterId()),
                n.getCoverId(),
                deliveries.holdingIn(n.getId(), me, Instant.now()).map(NovelDelivery::getId).orElse(null), n.getBaseWorkId());
    }
}
