package app.bookey.api.banner;

import app.bookey.api.banner.dto.BannerDtos.BannerAdminView;
import app.bookey.api.banner.dto.BannerDtos.BannerUpsertRequest;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.security.AuthAdmin;
import app.bookey.domain.banner.BannerKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Banner", description = "배너 관리")
@RestController
@RequestMapping("/admin/v1/banners")
@RequiredArgsConstructor
public class BannerAdminController {

    private final BannerService bannerService;

    @Operation(summary = "배너/공지 전체 목록 — 비활성·기간 외 포함")
    @GetMapping
    public List<BannerAdminView> list(@AuthenticationPrincipal AuthAdmin admin,
                                      @RequestParam(required = false) BannerKind kind) {
        requireOps(admin);
        return kind == null ? bannerService.adminList() : bannerService.adminList(kind);
    }

    @Operation(summary = "배너 생성")
    @PostMapping
    public BannerAdminView create(@AuthenticationPrincipal AuthAdmin admin,
                                  @Valid @RequestBody BannerUpsertRequest request) {
        requireOps(admin);
        return bannerService.create(request);
    }

    @Operation(summary = "배너 수정 — 전체 필드 교체")
    @PutMapping("/{id}")
    public BannerAdminView update(@AuthenticationPrincipal AuthAdmin admin,
                                  @PathVariable Long id,
                                  @Valid @RequestBody BannerUpsertRequest request) {
        requireOps(admin);
        return bannerService.update(id, request);
    }

    @Operation(summary = "배너 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        requireOps(admin);
        bannerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireOps(AuthAdmin admin) {
        if (!admin.role().canManageOps()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
