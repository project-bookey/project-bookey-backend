package app.bookey.api.curation;

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

    @Operation(summary = "에디터 픽 목록")
    @GetMapping
    public List<EditorPickView> list(@AuthenticationPrincipal AuthAdmin admin) {
        requireOps(admin);
        return editorPickAdminService.list();
    }

    @Operation(summary = "에디터 픽 추가")
    @PostMapping
    public EditorPickView create(@AuthenticationPrincipal AuthAdmin admin,
                                 @Valid @RequestBody EditorPickCreateRequest request) {
        requireOps(admin);
        return editorPickAdminService.create(request);
    }

    @Operation(summary = "에디터 픽 수정 — 정렬·메모")
    @PatchMapping("/{id}")
    public EditorPickView update(@AuthenticationPrincipal AuthAdmin admin,
                                 @PathVariable Long id,
                                 @Valid @RequestBody EditorPickUpdateRequest request) {
        requireOps(admin);
        return editorPickAdminService.update(id, request);
    }

    @Operation(summary = "에디터 픽 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        requireOps(admin);
        editorPickAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireOps(AuthAdmin admin) {
        if (!admin.role().canManageOps()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
