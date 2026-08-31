package app.bookey.domain.admin;

/** 관리자 권한 (§F13). */
public enum AdminRole {
    SUPER_ADMIN,
    OPERATOR,
    SUPPORT,
    VIEWER;

    public boolean canModerate() {
        return this == SUPER_ADMIN || this == OPERATOR;
    }

    public boolean canSanction() {
        return this == SUPER_ADMIN || this == OPERATOR;
    }

    public boolean canWarn() {
        return this == SUPER_ADMIN || this == OPERATOR || this == SUPPORT;
    }

    public boolean canEditBook() {
        return this == SUPER_ADMIN || this == OPERATOR;
    }

    public boolean canManageOps() {
        return this == SUPER_ADMIN;
    }
}
