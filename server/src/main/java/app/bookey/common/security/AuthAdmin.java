package app.bookey.common.security;

import app.bookey.domain.admin.AdminRole;

/** 인증된 관리자. /admin/v1/** 에서만 유효하다. 권한 판정은 {@link AdminRole} 에 맡긴다. */
public record AuthAdmin(Long id, String email, AdminRole role) {

    public boolean canModerate() {
        return role.canModerate();
    }

    public boolean canSanction() {
        return role.canSanction();
    }
}
