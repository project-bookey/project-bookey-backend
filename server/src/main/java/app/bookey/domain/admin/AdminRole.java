package app.bookey.domain.admin;

import java.util.ArrayList;
import java.util.List;

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

    /** 도서 병합 — 원본을 지우고 모든 기록을 옮겨 되돌릴 수 없으니 최고 관리자만. */
    public boolean canMergeBooks() {
        return this == SUPER_ADMIN;
    }

    /** 홈 배너·공지 팝업·에디터 픽 — 매일 손보는 운영 콘텐츠라 운영자(OPERATOR)도 다룬다. */
    public boolean canManageContent() {
        return this == SUPER_ADMIN || this == OPERATOR;
    }

    public boolean canManageOps() {
        return this == SUPER_ADMIN;
    }

    public boolean canManageAdmins() {
        return this == SUPER_ADMIN;
    }

    /** 전체 회원에게 가는 푸시 — 되돌릴 수 없으니 최고 관리자만. */
    public boolean canBroadcast() {
        return this == SUPER_ADMIN;
    }

    /** 결제·지갑 내역 열람 — 결제 문의에 답해야 하는 CS(SUPPORT)까지. */
    public boolean canViewPayments() {
        return this != VIEWER;
    }

    /** 회원 이메일 전체 보기 — 보기 전용(VIEWER)은 가린 값만 본다(2026-10-10 사용자 결정). */
    public boolean canViewPii() {
        return this != VIEWER;
    }

    public List<AdminCapability> capabilities() {
        List<AdminCapability> caps = new ArrayList<>();
        if (canModerate()) caps.add(AdminCapability.MODERATE);
        if (canSanction()) caps.add(AdminCapability.SANCTION);
        if (canWarn()) caps.add(AdminCapability.WARN);
        if (canHandleSupport()) caps.add(AdminCapability.HANDLE_SUPPORT);
        if (canEditBook()) caps.add(AdminCapability.EDIT_BOOK);
        if (canMergeBooks()) caps.add(AdminCapability.MERGE_BOOKS);
        if (canManageContent()) caps.add(AdminCapability.MANAGE_CONTENT);
        if (canManageOps()) caps.add(AdminCapability.MANAGE_OPS);
        if (canManageAdmins()) caps.add(AdminCapability.MANAGE_ADMINS);
        if (canBroadcast()) caps.add(AdminCapability.BROADCAST);
        if (canViewPayments()) caps.add(AdminCapability.VIEW_PAYMENTS);
        if (canViewPii()) caps.add(AdminCapability.VIEW_PII);
        return caps;
    }
}
