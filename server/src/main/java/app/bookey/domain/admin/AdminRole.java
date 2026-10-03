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

    /** 고객문의 답변과 FAQ 편집 — 보기 전용(VIEWER)만 막는다. CS 담당(SUPPORT)이 직접 다룬다. */
    public boolean canHandleSupport() {
        return this != VIEWER;
    }

    public boolean canEditBook() {
        return this == SUPER_ADMIN || this == OPERATOR;
    }

    public boolean canManageOps() {
        return this == SUPER_ADMIN;
    }
}
