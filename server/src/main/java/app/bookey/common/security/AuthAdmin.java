package app.bookey.common.security;

import app.bookey.domain.admin.AdminRole;

/** 인증된 관리자. /admin/v1/** 에서만 유효하다. */
public record AuthAdmin(Long id, String email, AdminRole role) {

    public boolean canModerate() {
        return role == AdminRole.SUPER_ADMIN || role == AdminRole.OPERATOR;
    }

    public boolean canSanction() {
        return role == AdminRole.SUPER_ADMIN || role == AdminRole.OPERATOR;
    }

    public boolean isSuper() {
        return role == AdminRole.SUPER_ADMIN;
    }
}
