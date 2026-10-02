package app.bookey.api.social;

import app.bookey.api.library.LibraryService;
import app.bookey.api.library.dto.LibraryDtos.LibrarySummary;
import app.bookey.api.library.dto.LibraryDtos.ReadingRecordView;
import app.bookey.api.post.PostService;
import app.bookey.api.session.dto.SessionDtos.StatsSummary;
import app.bookey.api.post.dto.PostDtos.PostView;
import app.bookey.api.social.dto.SocialDtos.LikerView;
import app.bookey.api.social.dto.SocialDtos.UserProfileView;
import app.bookey.api.social.dto.SocialDtos.VisitorView;
import app.bookey.common.security.AuthUser;
import app.bookey.api.stats.StatsService;
import app.bookey.common.support.PageResponse;
import app.bookey.domain.reading.ReadingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "유저 마이페이지 · 방문 기록 · 좋아요 열람 (§14.2·14.3)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PostService postService;
    private final LibraryService libraryService;
    private final StatsService statsService;

    /** 남의 기록 통계는 최대 1년까지만 — 내 화면('나')이 쓰는 범위와 같다. */
    private static final int MAX_STATS_DAYS = 366;

    @Operation(summary = "유저 프로필 — 열람 시 방문 기록이 남는다 (방문 수는 전체 공개)")
    @GetMapping("/users/{userId}/profile")
    public UserProfileView profile(@AuthenticationPrincipal AuthUser user,
                                   @PathVariable Long userId) {
        return profileService.profile(user.id(), userId);
    }

    @Operation(summary = "유저의 공개 독후감 — 피드에서 휘발된 글도 여기엔 축적")
    @GetMapping("/users/{userId}/posts")
    public PageResponse<PostView> posts(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable Long userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return postService.listPublicByUser(user.id(), userId, PageRequest.of(page, size));
    }

    @Operation(summary = "유저의 서재 — 마이페이지 선반용. 남의 서재면 각오(commitment) 메모는 비운다")
    @GetMapping("/users/{userId}/library")
    public PageResponse<ReadingRecordView> library(@AuthenticationPrincipal AuthUser user,
                                                   @PathVariable Long userId,
                                                   @RequestParam(required = false) ReadingStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        profileService.requireUser(userId);
        PageResponse<ReadingRecordView> result = libraryService.list(userId, status, PageRequest.of(page, size));
        if (userId.equals(user.id())) {
            return result;
        }
        return new PageResponse<>(
                result.content().stream().map(ProfileController::withoutPrivateNotes).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext());
    }

    @Operation(summary = "유저의 서재 상태별 개수")
    @GetMapping("/users/{userId}/library/summary")
    public LibrarySummary librarySummary(@PathVariable Long userId) {
        profileService.requireUser(userId);
        return libraryService.summary(userId);
    }

    @Operation(summary = "유저의 독서 통계 — 마이페이지 기록 카드(스트릭·히트맵)용, 최대 366일")
    @GetMapping("/users/{userId}/stats")
    public StatsSummary stats(@PathVariable Long userId,
                              @RequestParam(defaultValue = "90") int days) {
        profileService.requireUser(userId);
        return statsService.summary(userId, Math.min(days, MAX_STATS_DAYS));
    }

    private static ReadingRecordView withoutPrivateNotes(ReadingRecordView v) {
        return new ReadingRecordView(
                v.id(), v.round(), v.status(), v.book(), v.progress(), v.targetFinishDate(),
                v.startedAt(), v.finishedAt(), v.lastReadAt(), v.rating(), v.abandonReason(), null);
    }

    @Operation(summary = "내 방문자 목록 — 구독 회원 전용 (숫자는 프로필에서 전체 공개)")
    @GetMapping("/me/visitors")
    public PageResponse<VisitorView> visitors(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return profileService.visitors(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "내 글에 좋아요 누른 사람 — 글 주인 + 구독 회원 전용")
    @GetMapping("/posts/{postId}/likers")
    public PageResponse<LikerView> likers(@AuthenticationPrincipal AuthUser user,
                                          @PathVariable Long postId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return profileService.likers(user.id(), postId, PageRequest.of(page, size));
    }
}
