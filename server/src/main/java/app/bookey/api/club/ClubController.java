package app.bookey.api.club;

import app.bookey.api.club.dto.ClubDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Club", description = "독서 모임 — 코드 참가 · 진척 공유 · 체크포인트")
@RestController
@RequestMapping("/api/v1/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;
    private final ClubSeatService seatService;
    private final ClubNudgeService nudgeService;
    private final ClubCommunityService communityService;
    private final ClubActivityService activityService;
    private final ClubPlaceService placeService;

    @Operation(summary = "모임 만들기 — 초대 코드 자동 발급")
    @PostMapping
    public ClubHomeView create(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody CreateClubRequest request) {
        return clubService.create(user.id(), request);
    }

    @Operation(summary = "내 모임 목록")
    @GetMapping
    public PageResponse<ClubSummaryView> myClubs(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return clubService.myClubs(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "공개 모임 둘러보기")
    @GetMapping("/public")
    public PageResponse<ClubPreview> publicClubs(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return clubService.publicClubs(user.id(), PageRequest.of(page, size));
    }

    @Operation(summary = "초대 코드로 모임 미리보기 — 참가 전 확인용")
    @GetMapping("/preview")
    public ClubPreview preview(@AuthenticationPrincipal AuthUser user,
                               @RequestParam String code,
                               HttpServletRequest servletRequest) {
        String clientKey = user != null ? "u" + user.id() : "ip" + servletRequest.getRemoteAddr();
        return clubService.preview(user == null ? null : user.id(), code, clientKey);
    }

    @Operation(summary = "공개 모임 미리보기 — 추천 모임 상세 진입용")
    @GetMapping("/{clubId}/preview")
    public ClubPreview previewById(@AuthenticationPrincipal AuthUser user,
                                   @PathVariable Long clubId) {
        return clubService.previewById(user.id(), clubId);
    }

    @Operation(summary = "코드로 참가 — 도서 자동 등록 + 진척 공유 동의")
    @PostMapping("/join")
    public ClubHomeView join(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody JoinRequest request) {
        return clubService.join(user.id(), request);
    }

    @Operation(summary = "공개 모임 참가 — 추천 모임 상세에서 코드 없이 참가")
    @PostMapping("/{clubId}/join")
    public ClubHomeView joinPublic(@AuthenticationPrincipal AuthUser user,
                                   @PathVariable Long clubId,
                                   @Valid @RequestBody JoinPublicRequest request) {
        return clubService.joinPublic(user.id(), clubId, request);
    }

    @Operation(summary = "모임 홈 — 멤버 진척 · 체크포인트 그리드")
    @GetMapping("/{clubId}")
    public ClubHomeView home(@AuthenticationPrincipal AuthUser user, @PathVariable Long clubId) {
        return clubService.home(user.id(), clubId);
    }

    @Operation(summary = "모임 정보 수정 (호스트)")
    @PatchMapping("/{clubId}")
    public ClubHomeView update(@AuthenticationPrincipal AuthUser user,
                               @PathVariable Long clubId,
                               @Valid @RequestBody UpdateClubRequest request) {
        return clubService.update(user.id(), clubId, request);
    }

    @Operation(summary = "자리 늘리기 (호스트) — 자리당 책갈피 차감, 모임이 끝나면 늘린 자리는 사라진다")
    @PostMapping("/{clubId}/seats")
    public ClubSeatResult expandSeats(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long clubId,
                                      @Valid @RequestBody ExpandSeatsRequest request) {
        return seatService.expand(user.id(), clubId, request);
    }

    @Operation(summary = "초대 코드 회전 (호스트) — 유출 시 즉시 무효화")
    @PostMapping("/{clubId}/rotate-code")
    public Map<String, String> rotateCode(@AuthenticationPrincipal AuthUser user,
                                          @PathVariable Long clubId) {
        return Map.of("joinCode", clubService.rotateJoinCode(user.id(), clubId));
    }

    @Operation(summary = "내 공유 설정 변경 — 진척 공개 · 찌르기 수신")
    @PatchMapping("/{clubId}/sharing")
    public ResponseEntity<Void> updateSharing(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long clubId,
                                              @RequestBody UpdateSharingRequest request) {
        clubService.updateSharing(user.id(), clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모임 나가기")
    @DeleteMapping("/{clubId}/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long clubId) {
        clubService.leave(user.id(), clubId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "멤버 강퇴 (호스트) — 사유 필수")
    @PostMapping("/{clubId}/kick")
    public ResponseEntity<Void> kick(@AuthenticationPrincipal AuthUser user,
                                     @PathVariable Long clubId,
                                     @Valid @RequestBody KickRequest request) {
        clubService.kick(user.id(), clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "호스트 권한 넘기기")
    @PostMapping("/{clubId}/transfer-host")
    public ResponseEntity<Void> transferHost(@AuthenticationPrincipal AuthUser user,
                                             @PathVariable Long clubId,
                                             @Valid @RequestBody TransferHostRequest request) {
        clubService.transferHost(user.id(), clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모임 종료 (호스트)")
    @PostMapping("/{clubId}/end")
    public ResponseEntity<Void> end(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long clubId) {
        clubService.end(user.id(), clubId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모임 결산 — 완독률 · 총 시간 · 베스트 인용")
    @GetMapping("/{clubId}/result")
    public ClubResultView result(@AuthenticationPrincipal AuthUser user, @PathVariable Long clubId) {
        return clubService.result(user.id(), clubId);
    }

    @Operation(summary = "찌르기 — 프리셋 문구만, 대상당 24h 1회 · 하루 3회")
    @PostMapping("/{clubId}/nudges")
    public Map<String, Integer> nudge(@AuthenticationPrincipal AuthUser user,
                                      @PathVariable Long clubId,
                                      @Valid @RequestBody NudgeRequest request) {
        nudgeService.nudge(user.id(), clubId, request);
        return Map.of("remainingToday", nudgeService.remainingToday(user.id()));
    }

    @GetMapping("/{clubId}/chat")
    public app.bookey.api.club.dto.ClubCommunityDtos.ChatState chatState(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId){return communityService.chatState(user.id(),clubId);}
    @PostMapping("/{clubId}/chat/unlock")
    public app.bookey.api.club.dto.ClubCommunityDtos.UnlockResult unlockChat(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId){return communityService.unlock(user.id(),clubId);}
    @GetMapping("/{clubId}/chat/gift-candidates")
    public java.util.List<app.bookey.api.club.dto.ClubCommunityDtos.ChatGiftCandidate> giftCandidates(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId){return communityService.giftCandidates(user.id(),clubId);}
    @PostMapping("/{clubId}/chat/gifts")
    public app.bookey.api.club.dto.ClubCommunityDtos.UnlockResult giftChat(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@Valid @RequestBody app.bookey.api.club.dto.ClubCommunityDtos.GiftChatRequest request){return communityService.giftUnlock(user.id(),clubId,request.userId());}
    @GetMapping("/{clubId}/chat/messages")
    public app.bookey.api.club.dto.ClubCommunityDtos.ChatMessagesView chatMessages(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@RequestParam(required=false) Long beforeId){return communityService.chatMessages(user.id(),clubId,beforeId);}
    @PostMapping("/{clubId}/chat/messages")
    public app.bookey.api.club.dto.ClubCommunityDtos.ChatMessageView sendChat(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@Valid @RequestBody app.bookey.api.club.dto.ClubCommunityDtos.SendChatRequest request){return communityService.send(user.id(),clubId,request);}
    @GetMapping("/{clubId}/meetings")
    public java.util.List<app.bookey.api.club.dto.ClubCommunityDtos.MeetingView> meetings(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId){return communityService.meetingList(user.id(),clubId);}
    @PostMapping("/{clubId}/meetings")
    public app.bookey.api.club.dto.ClubCommunityDtos.MeetingView createMeeting(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@Valid @RequestBody app.bookey.api.club.dto.ClubCommunityDtos.UpsertMeetingRequest request){return communityService.createMeeting(user.id(),clubId,request);}
    @GetMapping("/{clubId}/meetings/{meetingId}")
    public app.bookey.api.club.dto.ClubCommunityDtos.MeetingView meetingDetail(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@PathVariable Long meetingId){return communityService.meetingDetail(user.id(),clubId,meetingId);}
    @PutMapping("/{clubId}/meetings/{meetingId}")
    public app.bookey.api.club.dto.ClubCommunityDtos.MeetingView updateMeeting(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@PathVariable Long meetingId,@Valid @RequestBody app.bookey.api.club.dto.ClubCommunityDtos.UpsertMeetingRequest request){return communityService.updateMeeting(user.id(),clubId,meetingId,request);}
    @DeleteMapping("/{clubId}/meetings/{meetingId}")
    public ResponseEntity<Void> cancelMeeting(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@PathVariable Long meetingId){communityService.cancelMeeting(user.id(),clubId,meetingId);return ResponseEntity.noContent().build();}
    @PostMapping("/{clubId}/meetings/{meetingId}/attendees/me")
    public app.bookey.api.club.dto.ClubCommunityDtos.MeetingView attend(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@PathVariable Long meetingId){return communityService.attend(user.id(),clubId,meetingId);}
    @DeleteMapping("/{clubId}/meetings/{meetingId}/attendees/me")
    public app.bookey.api.club.dto.ClubCommunityDtos.MeetingView unattend(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@PathVariable Long meetingId){return communityService.unattend(user.id(),clubId,meetingId);}
    @GetMapping("/{clubId}/places/search")
    public java.util.List<ClubPlaceService.PlaceView> searchPlaces(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@RequestParam String query){return placeService.search(user.id(),clubId,query);}
    @GetMapping("/{clubId}/places/geocode")
    public ClubPlaceService.Coordinates geocodePlace(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@RequestParam String address){return placeService.geocode(user.id(),clubId,address);}
    @GetMapping("/{clubId}/places/address-search")
    public java.util.List<ClubPlaceService.AddressView> searchAddresses(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@RequestParam String query){return placeService.searchAddresses(user.id(),clubId,query);}
    @GetMapping("/{clubId}/activity/current")
    public app.bookey.api.club.dto.ClubCommunityDtos.ActivitySessionView currentActivity(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId){return activityService.current(user.id(),clubId);}
    @PostMapping("/{clubId}/activity/start")
    public app.bookey.api.club.dto.ClubCommunityDtos.ActivitySessionView startActivity(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@RequestParam(required=false) Long meetingId){return activityService.start(user.id(),clubId,meetingId);}
    @PostMapping("/{clubId}/activity/end")
    public app.bookey.api.club.dto.ClubCommunityDtos.ActivityCardView endActivity(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId){return activityService.end(user.id(),clubId);}
    @GetMapping("/{clubId}/activity/cards")
    public java.util.List<app.bookey.api.club.dto.ClubCommunityDtos.ActivityCardView> activityCards(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId){return activityService.list(user.id(),clubId);}
    @PostMapping(value="/{clubId}/activity/cards/{cardId}",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public app.bookey.api.club.dto.ClubCommunityDtos.ActivityCardView decorateActivityCard(@AuthenticationPrincipal AuthUser user,@PathVariable Long clubId,@PathVariable Long cardId,@RequestParam(required=false) String caption,@RequestParam(required=false) String decorationsJson,@RequestPart(value="file",required=false) org.springframework.web.multipart.MultipartFile file){return activityService.decorate(user.id(),clubId,cardId,caption,decorationsJson,file);}
}
