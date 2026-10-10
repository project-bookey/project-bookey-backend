package app.bookey.api.banner;

import app.bookey.admin.support.AdminAuditService;
import app.bookey.admin.support.AuditSnapshot;
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
    private final AdminAuditService auditService;
    private final AuditSnapshot snapshot;

    @Operation(summary = "배너/공지 전체 목록 — 비활성·기간 외 포함")
    @GetMapping
    public List<BannerAdminView> list(@AuthenticationPrincipal AuthAdmin admin,
                                      @RequestParam(required = false) BannerKind kind) {
        requireContent(admin);
        return kind == null ? bannerService.adminList() : bannerService.adminList(kind);
    }

    @Operation(summary = "배너 생성")
    @PostMapping
    public BannerAdminView create(@AuthenticationPrincipal AuthAdmin admin,
                                  @Valid @RequestBody BannerUpsertRequest request) {
        requireContent(admin);
        BannerAdminView created = bannerService.create(request);
        auditService.log(admin, "CREATE_BANNER", "BANNER", created.id(), null, null, snapshot.of(created));
        return created;
    }

    @Operation(summary = "배너 수정 — 전체 필드 교체")
    @PutMapping("/{id}")
    public BannerAdminView update(@AuthenticationPrincipal AuthAdmin admin,
                                  @PathVariable Long id,
                                  @Valid @RequestBody BannerUpsertRequest request) {
        requireContent(admin);
        BannerAdminView before = find(id);
        BannerAdminView updated = bannerService.update(id, request);
        auditService.log(admin, "UPDATE_BANNER", "BANNER", id, null, snapshot.of(before), snapshot.of(updated));
        return updated;
    }

    @Operation(summary = "배너 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthAdmin admin, @PathVariable Long id) {
        requireContent(admin);
        BannerAdminView before = find(id);
        bannerService.delete(id);
        auditService.log(admin, "DELETE_BANNER", "BANNER", id, null, snapshot.of(before), null);
        return ResponseEntity.noContent().build();
    }

    private BannerAdminView find(Long id) {
        return bannerService.adminList().stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
    }

    /** 배너·공지는 매일 손보는 운영 콘텐츠라 운영자(OPERATOR)까지 다룬다. */
    private void requireContent(AuthAdmin admin) {
        if (!admin.role().canManageContent()) {
            throw ApiException.of(ErrorCode.ADMIN_FORBIDDEN);
        }
    }
}
