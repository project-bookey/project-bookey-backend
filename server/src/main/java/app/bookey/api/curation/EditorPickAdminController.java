package app.bookey.api.curation;

import app.bookey.admin.support.AdminAuditService;
import app.bookey.admin.support.AuditSnapshot;
import app.bookey.api.curation.dto.CurationDtos.EditorPickCreateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickUpdateRequest;
import app.bookey.api.curation.dto.CurationDtos.EditorPickView;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin EditorPick", description = "에디터 픽(추천 도서) 관리")
@RestController
@RequestMapping("/admin/v1/editor-picks")
@RequiredArgsConstructor
public class EditorPickAdminController {

    private final EditorPickAdminService editorPickAdminService;
    private final AdminAuditService auditService;
    private final AuditSnapshot snapshot;

    @Operation(summary = "에디터 픽 목록")
    @GetMapping
    public List<EditorPickView> list(@AuthenticationPrincipal AuthAdmin admin) {
        requireContent(admin);
        return editorPickAdminService.list();
    }

    @Operation(summary = "에디터 픽 추가")
    @PostMapping
    public EditorPickView create(@AuthenticationPrincipal AuthAdmin admin,
                                 @Valid @RequestBody EditorPickCreateRequest request) {
        requireContent(admin);
        EditorPickView created = editorPickAdminService.create(request);
        auditService.log(admin, "CREATE_EDITOR_PICK", "EDITOR_PICK", created.id(), null, null, snapshot.of(created));
        return created;
    }

    @Operation(summary = "에디터 픽 수정 — 정렬·메모")
    @PatchMapping("/{id}")
    public EditorPickView update(@AuthenticationPrincipal AuthAdmin admin,
                                 @PathVariable Long id,
                                 @Valid @RequestBody EditorPickUpdateRequest request) {
        requireContent(admin);
        EditorPickView before = find(id);
        EditorPickView updated = editorPickAdminService.update(id, request);
        auditService.log(admin, "UPDATE_EDITOR_PICK", "EDITOR_PICK", id, null,
                snapshot.of(before), snapshot.of(updated));
        return updated;
    }

    @Operation(summary = "에디터 픽 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        requireContent(admin);
        EditorPickView before = find(id);
        editorPickAdminService.delete(id);
        auditService.log(admin, "DELETE_EDITOR_PICK", "EDITOR_PICK", id, null, snapshot.of(before), null);
        return ResponseEntity.noContent().build();
    }

    private EditorPickView find(Long id) {
        return editorPickAdminService.list().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    /** 홈 '추천' 줄 — 운영자(OPERATOR)까지 다룬다. */
    private void requireContent(AuthAdmin admin) {
        if (!admin.role().canManageContent()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
