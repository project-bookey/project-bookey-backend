package app.bookey.api.club;

import app.bookey.api.club.dto.ClubNoteDtos.*;
import app.bookey.common.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Club Notebook", description = "모임 노트북 — 함께 꾸미는 페이지 · 사진")
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/notebook")
@RequiredArgsConstructor
public class ClubNoteController {

    private final ClubNoteService noteService;

    @Operation(summary = "노트북 — 페이지 목록(문서 없이 요약만)과 상한 정책. 끝난 모임은 readOnly")
    @GetMapping
    public ClubNotebookView notebook(@AuthenticationPrincipal AuthUser user, @PathVariable Long clubId) {
        return noteService.notebook(user.id(), clubId);
    }

    @Operation(summary = "페이지 만들기 — 마지막 뒤에 붙는다(모임당 30장). 다른 멤버에게 알림")
    @PostMapping("/pages")
    public ClubNotePageView createPage(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long clubId,
                                       @Valid @RequestBody(required = false) CreateClubNotePageRequest request) {
        return noteService.createPage(user.id(), clubId, request);
    }

    @Operation(summary = "페이지 — 문서 포함")
    @GetMapping("/pages/{pageId}")
    public ClubNotePageView page(@AuthenticationPrincipal AuthUser user,
                                 @PathVariable Long clubId,
                                 @PathVariable Long pageId) {
        return noteService.page(user.id(), clubId, pageId);
    }

    @Operation(summary = "페이지 저장 — 전체 덮어쓰기. version 이 다르면 409 CLUB_NOTE_CONFLICT, 최신 페이지를 다시 받아 저장한다")
    @PutMapping("/pages/{pageId}")
    public ClubNotePageSummaryView savePage(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable Long clubId,
                                            @PathVariable Long pageId,
                                            @Valid @RequestBody SaveClubNotePageRequest request) {
        return noteService.savePage(user.id(), clubId, pageId, request);
    }

    @Operation(summary = "페이지 지우기 — 만든 사람 또는 호스트·운영자")
    @DeleteMapping("/pages/{pageId}")
    public ResponseEntity<Void> deletePage(@AuthenticationPrincipal AuthUser user,
                                           @PathVariable Long clubId,
                                           @PathVariable Long pageId) {
        noteService.deletePage(user.id(), clubId, pageId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "노트북 사진 올리기 — 응답 id·url 을 문서의 photo 요소에 넣어 저장해야 24시간 뒤 정리되지 않는다")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClubNoteImageView uploadImage(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable Long clubId,
                                         @RequestPart("file") MultipartFile file) {
        return noteService.uploadImage(user.id(), clubId, file);
    }
}
