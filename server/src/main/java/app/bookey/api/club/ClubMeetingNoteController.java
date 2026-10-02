package app.bookey.api.club;

import app.bookey.api.club.dto.ClubMeetingNoteDtos.*;
import app.bookey.common.security.AuthUser;
import app.bookey.common.support.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 모임 공유 노트. 실시간 편집은 웹소켓 {@code /ws/clubs/{clubId}/meetings/{meetingId}/note} 로 하고,
 * 연결이 끊겼을 때는 같은 연산을 여기 REST 로 보낸다.
 */
@Tag(name = "Club Meeting Note", description = "모임 공유 노트 — 모임마다 대형노트 한 권, 멤버가 함께 꾸민다")
@RestController
@RequestMapping("/api/v1/clubs/{clubId}")
@RequiredArgsConstructor
public class ClubMeetingNoteController {

    private final ClubMeetingNoteService noteService;

    @Operation(summary = "모임 노트 피드 — 빈 노트는 빼고 최근에 고친 순. 썸네일용 문서 포함")
    @GetMapping("/meeting-notes")
    public PageResponse<MeetingNoteView> meetingNoteFeed(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable Long clubId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "18") int size) {
        return noteService.listByClub(user.id(), clubId, page, size);
    }

    @Operation(summary = "모임 노트 — 아직 아무도 쓰지 않았으면 빈 노트(id null, version 0)")
    @GetMapping("/meetings/{meetingId}/note")
    public MeetingNoteView meetingNote(@AuthenticationPrincipal AuthUser user,
                               @PathVariable Long clubId,
                               @PathVariable Long meetingId) {
        return noteService.get(user.id(), clubId, meetingId);
    }

    @Operation(summary = "모임 노트 연산 적용 — 요소 id 기준 upsert·delete. 실시간 연결이 끊겼을 때 쓴다")
    @PostMapping("/meetings/{meetingId}/note/ops")
    public MeetingNoteOpsResult applyMeetingNoteOps(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable Long clubId,
                                         @PathVariable Long meetingId,
                                         @Valid @RequestBody ApplyMeetingNoteOpsRequest request) {
        return noteService.applyOps(user.id(), clubId, meetingId, request.ops(), request.clientId());
    }

    @Operation(summary = "모임 노트 사진 올리기 — 응답 id 를 photo 요소의 imageId 로 넣어 보내야 24시간 뒤 정리되지 않는다")
    @PostMapping(value = "/meetings/{meetingId}/note/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeetingNoteImageView uploadMeetingNoteImage(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable Long clubId,
                                            @PathVariable Long meetingId,
                                            @RequestPart("file") MultipartFile file) {
        return noteService.uploadImage(user.id(), clubId, meetingId, file);
    }
}
