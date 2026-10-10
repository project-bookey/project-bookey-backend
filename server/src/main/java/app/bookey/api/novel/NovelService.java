package app.bookey.api.novel;

import app.bookey.api.novel.dto.NovelDtos.*;
import app.bookey.common.error.*;
import app.bookey.common.support.*;
import app.bookey.domain.novel.*;
import app.bookey.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NovelService {
    private final NovelRepository novels;
    private final NovelMemberRepository members;
    private final NovelChapterRepository chapters;
    private final NovelDraftRepository drafts;
    private final NovelCoverRepository covers;
    private final UserRepository users;
    private final RateLimiter limiter;

    @Transactional
    public NovelDetail create(Long me, CreateNovelRequest request) {
        limiter.require("novel:create:" + me, 5, Duration.ofMinutes(1));
        Novel n = novels.saveAndFlush(new Novel(me, request.kind(), request.title(), request.description(),
                request.genre(), request.memberLimit() == null ? 6 : request.memberLimit(),
                request.chapterLimit() == null ? 20 : request.chapterLimit(),
                request.turnHours() == null ? 24 : request.turnHours(), request.isPublic(),
                UUID.randomUUID().toString().replace("-", "")));
        members.saveAndFlush(new NovelMember(n.getId(), me, true));
        attachCover(n, me, request.coverId());
        return detail(n, me);
    }

    @Transactional(readOnly = true)
    public PageResponse<NovelSummary> feed(Long me, NovelKind kind, NovelStatus status, Pageable page) {
        return PageResponse.of(novels.feed(kind, status, page), n -> summary(n, me));
    }
    @Transactional(readOnly = true)
    public PageResponse<NovelSummary> mine(Long me, NovelKind kind, Pageable page) {
        return PageResponse.of(novels.mine(me, kind, page), n -> summary(n, me));
    }
    @Transactional(readOnly = true)
    public List<NovelSummary> myTurns(Long me) {
        return novels.myTurns(me, Instant.now(), PageRequest.of(0, 20)).stream()
                .map(n -> summary(n, me)).toList();
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

    /** 공개는 누구나 신청, 비공개는 코드로 신청. 승인 전에는 집필 순서에 들어가지 않는다. */
    @Transactional
    public NovelDetail apply(Long me, Long id) {
        Novel n = readable(me, id, true);
        if (!n.isPublic() && !n.isOwner(me)) throw ApiException.of(ErrorCode.FORBIDDEN);
        return applyTo(n, me);
    }
    @Transactional
    public NovelDetail joinByCode(Long me, String code) {
        Novel found = novels.findByInviteCode(code.strip()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Novel n = novels.lockById(found.getId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (users.isTerminated(n.getOwnerId())) throw ApiException.of(ErrorCode.NOT_FOUND);
        return applyTo(n, me);
    }
    private NovelDetail applyTo(Novel n, Long me) {
        if (n.getKind() != NovelKind.RELAY || n.getStatus() == NovelStatus.COMPLETED)
            throw new ApiException(ErrorCode.CONFLICT, "참가자를 받고 있지 않은 작품이에요.");
        var existing = members.findByNovelIdAndUserId(n.getId(), me);
        if (existing.isPresent() && List.of("ACTIVE", "PENDING").contains(existing.get().getStatus()))
            return detail(n, me);
        limiter.require("novel:apply:" + me, 10, Duration.ofMinutes(1));
        if (active(n).size() >= n.getMemberLimit()) throw ApiException.of(ErrorCode.NOVEL_FULL);
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
            if (active(n).size() >= n.getMemberLimit()) throw ApiException.of(ErrorCode.NOVEL_FULL);
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
        n.complete(); return detail(n, me);
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
        NovelChapter c = chapters.saveAndFlush(new NovelChapter(id, me, n.getChapterCount() + 1,
                request.title(), request.body()));
        drafts.findByNovelIdAndUserId(id, me).ifPresent(drafts::delete);
        n.publish(writerIds(n), Instant.now());
        return chapterView(c);
    }
    @Transactional(readOnly = true)
    public PageResponse<NovelChapterSummary> chapters(Long me, Long id, Pageable page) {
        requireReadChapters(me, id);
        return PageResponse.of(chapters.findByNovelIdOrderByChapterNumberAsc(id, page),
                c -> new NovelChapterSummary(c.getId(), c.getChapterNumber(), c.getTitle(),
                        nickname(c.getAuthorId()), excerpt(c.getBody()), c.getCreatedAt()));
    }
    @Transactional(readOnly = true)
    public NovelChapterView chapter(Long me, Long id, int number) {
        requireReadChapters(me, id);
        return chapterView(chapters.findByNovelIdAndChapterNumber(id, number)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)));
    }
    private NovelChapterView chapterView(NovelChapter c) {
        return new NovelChapterView(c.getId(), c.getNovelId(), c.getChapterNumber(), c.getTitle(),
                c.getBody(), nickname(c.getAuthorId()), c.getCreatedAt());
    }
    @Transactional
    public void expire(Long id) { novels.lockById(id).ifPresent(this::expire); }
    private void expire(Novel n) {
        var ids = writerIds(n);
        n.expire(ids, Instant.now());
        if (n.getKind() == NovelKind.RELAY && n.getStatus() == NovelStatus.ONGOING
                && !ids.contains(n.getCurrentWriterId())) n.skip(ids, Instant.now());
    }
    private void requireReadChapters(Long me, Long id) {
        Novel n = readable(me, id, false);
        if (!n.isPublic() && !n.isOwner(me) && members.findByNovelIdAndUserId(id, me)
                .filter(m -> "ACTIVE".equals(m.getStatus())).isEmpty()) throw ApiException.of(ErrorCode.FORBIDDEN);
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
    private NovelSummary summary(Novel n, Long me) {
        String url = n.getCoverId() == null ? null : covers.findById(n.getCoverId()).map(NovelCover::getUrl).orElse(null);
        boolean myTurn = n.canWrite(me) && (n.getTurnDueAt() == null || Instant.now().isBefore(n.getTurnDueAt()));
        return new NovelSummary(n.getId(), n.getKind(), n.getStatus(), n.getTitle(), n.getDescription(), n.getGenre(),
                url, nickname(n.getOwnerId()), active(n).size(), n.getMemberLimit(), n.getChapterCount(),
                n.getChapterLimit(), n.isPublic(), n.isOwner(me), myTurn, n.getTurnDueAt(), n.getCreatedAt());
    }
    private NovelDetail detail(Novel n, Long me) {
        var active = active(n);
        String membership = members.findByNovelIdAndUserId(n.getId(), me).map(NovelMember::getStatus).orElse("NONE");
        var applications = n.isOwner(me) ? members.findByNovelIdAndStatusOrderByIdAsc(n.getId(), "PENDING").stream()
                .filter(m -> !users.isTerminated(m.getUserId())).map(m -> member(n, m)).toList() : List.<NovelMemberView>of();
        return new NovelDetail(summary(n, me), active.stream().map(m -> member(n, m)).toList(), applications, membership,
                n.isPublic() && n.getKind() == NovelKind.RELAY && n.getStatus() != NovelStatus.COMPLETED
                        && !List.of("ACTIVE", "PENDING").contains(membership) && active.size() < n.getMemberLimit(),
                n.canWrite(me), n.isOwner(me) && n.getStatus() == NovelStatus.RECRUITING && active.size() >= 2,
                n.isOwner(me) && n.getStatus() == NovelStatus.ONGOING && n.getChapterCount() > 0,
                n.getTurnNumber(), n.getCurrentWriterId() == null ? null : nickname(n.getCurrentWriterId()),
                n.isOwner(me) || "ACTIVE".equals(membership) ? n.getInviteCode() : null, n.getCoverId());
    }
}
